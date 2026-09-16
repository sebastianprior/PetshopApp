package com.petshop.app.service;

import org.springframework.stereotype.Component;

@Component
public class AdminGuard {

    private final JwtUtil jwtUtil;

    public AdminGuard(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public boolean isAdmin(String token) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return false;
        }
        return "ADMIN".equals(jwtUtil.extractRole(token));
    }
}
