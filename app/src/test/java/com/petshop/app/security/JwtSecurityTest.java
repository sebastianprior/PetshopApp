package com.petshop.app.security;

import com.petshop.app.service.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SEC-W03: tokens vencidos, alterados o con algoritmo "none" se rechazan, y el access token dura 15 minutos como máximo. */
class JwtSecurityTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret";
    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);

    private String signedWith(String secret, Date issuedAt, Date expiration) {
        var builder = Jwts.builder().subject("user-1").claim("role", "ADMIN").issuedAt(issuedAt);
        if (expiration != null) {
            builder.expiration(expiration);
        }
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anExpiredTokenIsRejected() {
        Date past = new Date(System.currentTimeMillis() - 3_600_000);
        String expired = signedWith(SECRET, new Date(past.getTime() - 60_000), past);

        assertThat(jwtUtil.isTokenValid(expired)).isFalse();
    }

    @Test
    void aTamperedPayloadIsRejected() {
        String token = jwtUtil.generateToken("user-1", "u@example.com", "CUSTOMER");
        String[] parts = token.split("\\.");
        String forgedPayload = b64("{\"sub\":\"user-1\",\"role\":\"ADMIN\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + "}");

        assertThat(jwtUtil.isTokenValid(parts[0] + "." + forgedPayload + "." + parts[2])).isFalse();
    }

    @Test
    void anUnsignedTokenWithAlgNoneIsRejected() {
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"user-1\",\"role\":\"ADMIN\",\"exp\":" + (System.currentTimeMillis() / 1000 + 3600) + "}");

        assertThat(jwtUtil.isTokenValid(header + "." + payload + ".")).isFalse();
        assertThat(jwtUtil.isTokenValid(header + "." + payload)).isFalse();
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        String foreign = signedWith("otra-clave-otra-clave-otra-clave-otra-clave", new Date(), new Date(System.currentTimeMillis() + 60_000));

        assertThat(jwtUtil.isTokenValid(foreign)).isFalse();
    }

    @Test
    void aCorrectlySignedTokenWithoutExpirationIsRejected() {
        String neverExpires = signedWith(SECRET, new Date(), null);

        assertThat(jwtUtil.isTokenValid(neverExpires)).isFalse();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtUtil.isTokenValid("abc.def.ghi")).isFalse();
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    @Test
    void issuedTokensLiveAtMostFifteenMinutes() {
        var claims = jwtUtil.parseToken(jwtUtil.generateToken("user-1", "u@example.com", "CUSTOMER"));

        long lifetimeSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(lifetimeSeconds).isLessThanOrEqualTo(900);
    }

    @Test
    void aLongerLifetimeThanFifteenMinutesCannotBeConfigured() {
        assertThatThrownBy(() -> new JwtUtil(SECRET, 24 * 60 * 60 * 1000L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtUtil(SECRET, 0)).isInstanceOf(IllegalStateException.class);
        new JwtUtil(SECRET, JwtUtil.MAX_ACCESS_TOKEN_MS);
    }

    @Test
    void theShippedConfigurationRespectsTheFifteenMinuteLimit() throws Exception {
        Properties props = shippedProperties();

        assertThat(Long.parseLong(props.getProperty("petshop.jwt.expiration-ms"))).isLessThanOrEqualTo(900_000L);
    }

    @Test
    void thereIsNoDefaultSecretInTheConfiguration() throws Exception {
        String secretSetting = shippedProperties().getProperty("petshop.jwt.secret");

        assertThat(secretSetting).isEqualTo("${JWT_SECRET:}");
    }

    @Test
    void withoutASecretARandomOneIsUsedAndTokensAreNotInterchangeable() {
        JwtUtil first = new JwtUtil("", 60_000);
        JwtUtil second = new JwtUtil(null, 60_000);

        String token = first.generateToken("user-1", "u@example.com", "ADMIN");

        assertThat(first.isTokenValid(token)).isTrue();
        assertThat(second.isTokenValid(token)).isFalse();
    }

    @Test
    void aTooShortSecretIsRefused() {
        assertThatThrownBy(() -> new JwtUtil("corto", 60_000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void errorResponsesNeverIncludeStacktraces() throws Exception {
        Properties props = shippedProperties();

        assertThat(props.getProperty("spring.web.error.include-stacktrace")).isEqualTo("never");
        assertThat(props.getProperty("spring.web.error.include-message")).isEqualTo("never");
        assertThat(props.getProperty("spring.web.error.include-exception")).isEqualTo("false");
    }

    private Properties shippedProperties() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }
        return props;
    }
}
