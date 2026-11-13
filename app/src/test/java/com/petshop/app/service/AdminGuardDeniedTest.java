package com.petshop.app.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin una sesión válida la respuesta es 401; con una sesión válida que no es de administrador, 403. */
class AdminGuardDeniedTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret";
    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);
    private final AdminGuard guard = new AdminGuard(jwtUtil);

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private int status(String token) {
        return guard.denied(token).getStatusCode().value();
    }

    @Test
    void aValidCustomerSessionIsForbidden() {
        ResponseEntity<Map<String, String>> response = guard.denied(jwtUtil.generateToken("u1", "c@e.com", "CUSTOMER"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).containsEntry("error", "Requiere permisos de administrador");
    }

    @Test
    void noTokenIsUnauthorized() {
        ResponseEntity<Map<String, String>> response = guard.denied(null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("error", "Iniciá sesión para continuar");
        assertThat(status("")).isEqualTo(401);
        assertThat(status("   ")).isEqualTo(401);
    }

    @Test
    void garbageAndTamperedTokensAreUnauthorized() {
        String valid = jwtUtil.generateToken("u2", "a@e.com", "ADMIN");
        String[] parts = valid.split("\\.");

        assertThat(status("esto-no-es-un-jwt")).isEqualTo(401);
        assertThat(status(parts[0] + "." + parts[1] + "." + b64("firma-falsa-firma-falsa-firma-falsa"))).isEqualTo(401);
        assertThat(status(parts[0] + "." + b64("{\"sub\":\"u2\",\"role\":\"ADMIN\"}") + "." + parts[2])).isEqualTo(401);
    }

    @Test
    void anUnsignedAlgNoneTokenIsUnauthorized() {
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String body = b64("{\"sub\":\"u2\",\"email\":\"a@e.com\",\"role\":\"ADMIN\",\"exp\":" + (System.currentTimeMillis() / 1000 + 900) + "}");

        assertThat(status(header + "." + body + ".")).isEqualTo(401);
        assertThat(status(header + "." + body)).isEqualTo(401);
    }

    @Test
    void aTokenSignedByAnotherIssuerIsUnauthorizedEvenWithAdminRole() {
        SecretKey otherKey = Keys.hmacShaKeyFor("otra-clave-otra-clave-otra-clave-otra-clave".getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder().subject("u2").claim("email", "a@e.com").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(otherKey).compact();

        assertThat(status(foreign)).isEqualTo(401);
        assertThat(guard.isAdmin(foreign)).isFalse();
    }

    @Test
    void anExpiredAdminTokenIsUnauthorizedSoTheClientCanRefreshItsSession() throws Exception {
        JwtUtil shortLived = new JwtUtil(SECRET, 1);
        String token = shortLived.generateToken("u2", "a@e.com", "ADMIN");
        Thread.sleep(30);

        assertThat(new AdminGuard(jwtUtil).denied(token).getStatusCode().value()).isEqualTo(401);
        assertThat(guard.isAdmin(token)).isFalse();
    }
}
