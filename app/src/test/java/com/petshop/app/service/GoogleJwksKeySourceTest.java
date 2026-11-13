package com.petshop.app.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Las claves públicas de Google se piden por HTTP, se guardan y se vuelven a pedir con un límite de frecuencia. */
class GoogleJwksKeySourceTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile String body = "{}";
    private RSAPublicKey rsaKey;
    private String certsUrl;

    @BeforeEach
    void startServer() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        rsaKey = (RSAPublicKey) pair.getPublic();
        body = jwks("kid-1");

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/certs", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        certsUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/certs";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String b64(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String jwks(String kid) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"alg\":\"RS256\",\"n\":\"" + b64(rsaKey.getModulus())
                + "\",\"e\":\"" + b64(rsaKey.getPublicExponent()) + "\"}]}";
    }

    @Test
    void parseKeepsOnlyRsaKeysThatHaveAnId() throws Exception {
        String json = "{\"keys\":[" + jwks("kid-1").replaceAll("^\\{\"keys\":\\[|\\]\\}$", "")
                + ",{\"kty\":\"EC\",\"kid\":\"ec-key\",\"crv\":\"P-256\",\"x\":\"AA\",\"y\":\"AA\"}"
                + "," + jwks("").replaceAll("^\\{\"keys\":\\[|\\]\\}$", "") + "]}";

        Map<String, PublicKey> keys = GoogleJwksKeySource.parse(json);

        assertThat(keys).containsOnlyKeys("kid-1");
        assertThat(((RSAPublicKey) keys.get("kid-1")).getModulus()).isEqualTo(rsaKey.getModulus());
        assertThat(((RSAPublicKey) keys.get("kid-1")).getPublicExponent()).isEqualTo(rsaKey.getPublicExponent());
    }

    @Test
    void parseReturnsNoKeysWhenThereIsNoKeysArray() throws Exception {
        assertThat(GoogleJwksKeySource.parse("{}")).isEmpty();
    }

    @Test
    void aBlankOrMissingKeyIdNeverCallsGoogle() {
        GoogleJwksKeySource source = new GoogleJwksKeySource(certsUrl);

        assertThat(source.keyFor(null)).isEmpty();
        assertThat(source.keyFor("  ")).isEmpty();
        assertThat(requests.get()).isZero();
    }

    @Test
    void fetchesTheKeysTheFirstTimeAndThenServesThemFromMemory() {
        GoogleJwksKeySource source = new GoogleJwksKeySource(certsUrl);

        assertThat(source.keyFor("kid-1")).isPresent();
        assertThat(source.keyFor("kid-1")).isPresent();
        assertThat(source.keyFor("kid-1")).isPresent();

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void anUnknownKeyIdDoesNotHammerGoogleWithinTheRefreshInterval() {
        GoogleJwksKeySource source = new GoogleJwksKeySource(certsUrl);
        assertThat(source.keyFor("kid-1")).isPresent();

        assertThat(source.keyFor("otro-kid")).isEmpty();
        assertThat(source.keyFor("otro-kid")).isEmpty();

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void whenGoogleAnswersWithAnErrorThereIsNoKeyAndNoException() {
        status = 500;
        GoogleJwksKeySource source = new GoogleJwksKeySource(certsUrl);

        assertThat(source.keyFor("kid-1")).isEmpty();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void whenGoogleIsUnreachableThereIsNoKeyAndNoException() {
        server.stop(0);
        GoogleJwksKeySource source = new GoogleJwksKeySource(certsUrl);

        assertThat(source.keyFor("kid-1")).isEmpty();
    }

    @Test
    void aBrokenResponseBodyIsTreatedAsNoKeys() {
        body = "esto no es json";
        GoogleJwksKeySource source = new GoogleJwksKeySource(certsUrl);

        assertThat(source.keyFor("kid-1")).isEmpty();
    }
}
