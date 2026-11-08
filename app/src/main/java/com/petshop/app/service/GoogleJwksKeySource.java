package com.petshop.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Claves públicas con las que Google firma sus tokens (https://www.googleapis.com/oauth2/v3/certs). Se guardan en memoria
 * una hora; si llega un token con una clave que no se conoce, se vuelve a pedir la lista (como máximo cada 5 minutos).
 */
@Component
public class GoogleJwksKeySource implements GoogleTokenVerifier.KeySource {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleJwksKeySource.class);
    private static final Duration KEY_LIFETIME = Duration.ofHours(1);
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(5);

    private final String certsUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, PublicKey> keys = Map.of();
    private long loadedAtMs;
    private long lastAttemptMs;

    public GoogleJwksKeySource(@Value("${petshop.google.certs-url:https://www.googleapis.com/oauth2/v3/certs}") String certsUrl) {
        this.certsUrl = certsUrl;
    }

    @Override
    public synchronized Optional<PublicKey> keyFor(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        boolean expired = now - loadedAtMs > KEY_LIFETIME.toMillis();
        boolean mayRefresh = now - lastAttemptMs > MIN_REFRESH_INTERVAL.toMillis();
        if ((expired || !keys.containsKey(keyId)) && mayRefresh) {
            refresh(now);
        }
        return Optional.ofNullable(keys.get(keyId));
    }

    private void refresh(long now) {
        lastAttemptMs = now;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(certsUrl)).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warn("Google respondió {} al pedir las claves públicas", response.statusCode());
                return;
            }
            keys = parse(response.body());
            loadedAtMs = now;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("No se pudieron obtener las claves públicas de Google: {}", e.getMessage());
        }
    }

    /** Convierte la lista de claves (JWKS) en claves públicas RSA por identificador. */
    static Map<String, PublicKey> parse(String jwksJson) throws Exception {
        Map<String, PublicKey> result = new HashMap<>();
        JsonNode root = new ObjectMapper().readTree(jwksJson);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        for (JsonNode key : root.path("keys")) {
            if (!"RSA".equals(key.path("kty").asText()) || key.path("kid").asText("").isEmpty()) {
                continue;
            }
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(key.path("n").asText()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(key.path("e").asText()));
            result.put(key.path("kid").asText(), factory.generatePublic(new RSAPublicKeySpec(modulus, exponent)));
        }
        return result;
    }
}
