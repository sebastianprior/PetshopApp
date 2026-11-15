package com.petshop.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 60_000);
    }

    @Test
    void extractUserIdReturnsIdUsedToGenerateToken() {
        String token = jwtUtil.generateToken("user-123", "user@example.com", "CUSTOMER");

        String userId = jwtUtil.extractUserId(token);

        assertThat(userId).isEqualTo("user-123");
    }

    @Test
    void extractRoleReturnsRoleUsedToGenerateToken() {
        String token = jwtUtil.generateToken("user-123", "user@example.com", "ADMIN");

        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void isTokenValidReturnsFalseForCorruptedOrDifferentlySignedToken() {
        String token = jwtUtil.generateToken("user-123", "user@example.com", "CUSTOMER");
        String corrupted = token.substring(0, token.length() - 2) + "xx";
        JwtUtil differentKeyJwtUtil = new JwtUtil("different-secret-different-secret-different-secret", 60_000);

        assertThat(jwtUtil.isTokenValid(corrupted)).isFalse();
        assertThat(differentKeyJwtUtil.isTokenValid(token)).isFalse();
    }
}
