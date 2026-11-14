package com.petshop.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminGuardTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret";

    private JwtUtil jwtUtil;
    private AdminGuard adminGuard;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 60_000);
        adminGuard = new AdminGuard(jwtUtil);
    }

    @Test
    void isAdminReturnsTrueForTokenWithAdminRole() {
        String token = jwtUtil.generateToken("user-1", "admin@example.com", "ADMIN");

        assertThat(adminGuard.isAdmin(token)).isTrue();
    }

    @Test
    void isAdminReturnsFalseForTokenWithCustomerRole() {
        String token = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");

        assertThat(adminGuard.isAdmin(token)).isFalse();
    }

    @Test
    void isAdminReturnsFalseForMissingOrInvalidToken() {
        assertThat(adminGuard.isAdmin(null)).isFalse();
        assertThat(adminGuard.isAdmin("not-a-real-jwt")).isFalse();
    }
}
