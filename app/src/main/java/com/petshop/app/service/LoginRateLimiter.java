package com.petshop.app.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MINUTES = 15;
    private static final long LOCKOUT_MINUTES = 15;

    private static class Attempt {
        int count;
        Instant windowStart;
        Instant lockedUntil;
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public long secondsLockedOut(String email) {
        Attempt a = attempts.get(normalize(email));
        if (a == null || a.lockedUntil == null) {
            return 0;
        }
        long remaining = Instant.now().until(a.lockedUntil, ChronoUnit.SECONDS);
        return Math.max(remaining, 0);
    }

    public void recordFailure(String email) {
        String key = normalize(email);
        if (key.isEmpty()) {
            return;
        }
        attempts.compute(key, (k, existing) -> {
            Instant now = Instant.now();
            Attempt a = existing;
            if (a == null || now.isAfter(a.windowStart.plus(WINDOW_MINUTES, ChronoUnit.MINUTES))) {
                a = new Attempt();
                a.windowStart = now;
                a.count = 0;
            }
            a.count++;
            if (a.count >= MAX_ATTEMPTS) {
                a.lockedUntil = now.plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES);
            }
            return a;
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
