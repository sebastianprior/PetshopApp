package com.petshop.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter();
    }

    @Test
    void locksOutAfterFiveFailuresAndAllowsAfterSuccess() {
        String email = "user@example.com";

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(email);
            assertThat(limiter.secondsLockedOut(email)).isEqualTo(0);
        }

        limiter.recordFailure(email);
        assertThat(limiter.secondsLockedOut(email)).isGreaterThan(0);

        limiter.recordSuccess(email);
        assertThat(limiter.secondsLockedOut(email)).isEqualTo(0);
    }

    @Test
    void isCaseInsensitiveAndTrimsWhitespace() {
        limiter.recordFailure("User@Example.com");
        limiter.recordFailure(" user@example.com ");
        limiter.recordFailure("USER@EXAMPLE.COM");
        limiter.recordFailure("user@example.com");
        limiter.recordFailure("user@example.com");

        assertThat(limiter.secondsLockedOut("user@example.com")).isGreaterThan(0);
    }

    @Test
    void differentEmailsAreTrackedIndependently() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("victim@example.com");
        }

        assertThat(limiter.secondsLockedOut("victim@example.com")).isGreaterThan(0);
        assertThat(limiter.secondsLockedOut("other@example.com")).isEqualTo(0);
    }
}
