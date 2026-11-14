package com.petshop.app.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

@Component
public class JwtUtil {

    /** Vida máxima de un access token (15 minutos); la sesión se extiende con el refresh token. */
    public static final long MAX_ACCESS_TOKEN_MS = 15 * 60 * 1000L;

    private static final int MIN_SECRET_BYTES = 32;
    private static final Logger LOG = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${petshop.jwt.secret:}") String secret,
                    @Value("${petshop.jwt.expiration-ms}") long expirationMs) {
        if (expirationMs <= 0 || expirationMs > MAX_ACCESS_TOKEN_MS) {
            throw new IllegalStateException("petshop.jwt.expiration-ms debe estar entre 1 y " + MAX_ACCESS_TOKEN_MS
                    + " ms (15 minutos); valor configurado: " + expirationMs);
        }
        this.key = Keys.hmacShaKeyFor(resolveSecret(secret));
        this.expirationMs = expirationMs;
    }

    /** Sin JWT_SECRET se genera un secreto aleatorio por ejecución: no hay un valor por defecto que se pueda conocer. */
    private static byte[] resolveSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[64];
            new SecureRandom().nextBytes(random);
            LOG.warn("JWT_SECRET no está configurado: se usa un secreto aleatorio solo para esta ejecución "
                    + "(los access tokens dejan de valer al reiniciar). Definí JWT_SECRET para un entorno real.");
            return random;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("petshop.jwt.secret debe tener al menos " + MIN_SECRET_BYTES + " caracteres");
        }
        return bytes;
    }

    public String generateToken(String userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getExpiration() == null) {
            throw new JwtException("El token no tiene vencimiento");
        }
        return claims;
    }

    public String extractUserId(String token) {
        return parseToken(token).getSubject();
    }

    public String extractEmail(String token) {
        return parseToken(token).get("email", String.class);
    }

    public String extractRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
