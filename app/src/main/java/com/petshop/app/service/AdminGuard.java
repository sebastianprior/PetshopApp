package com.petshop.app.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

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

    /**
     * Respuesta para quien no puede usar una operación de administrador: 401 si no hay una sesión válida (sin token,
     * vencido, con firma alterada, de otro emisor...) y 403 si la sesión es válida pero no es de administrador.
     */
    public ResponseEntity<Map<String, String>> denied(String token) {
        boolean noSession = token == null || !jwtUtil.isTokenValid(token);
        return ResponseEntity.status(noSession ? 401 : 403).body(Map.of("error",
                noSession ? "Iniciá sesión para continuar" : "Requiere permisos de administrador"));
    }
}
