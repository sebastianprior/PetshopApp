package com.petshop.app.service;

import com.petshop.app.model.RefreshToken;
import com.petshop.app.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Sesiones de larga duración: el access token vive 15 minutos y se renueva con un refresh token de un solo uso (rotación).
 * Si se presenta un refresh token ya usado se asume robo y se revocan todos los del usuario.
 */
@Service
public class RefreshTokenService {

    public record Rotation(String userId, String refreshToken) {}

    private final RefreshTokenRepository repository;
    private final long ttlMs;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${petshop.jwt.refresh-expiration-ms:604800000}") long ttlMs) {
        this.repository = repository;
        this.ttlMs = ttlMs;
    }

    public String issue(String userId) {
        String raw = TokenHasher.newToken();
        repository.save(new RefreshToken(TokenHasher.hash(raw), userId, Instant.now().toEpochMilli() + ttlMs));
        return raw;
    }

    /** Consume el refresh token recibido y devuelve uno nuevo; vacío si es desconocido, vencido, revocado o reutilizado. */
    public Optional<Rotation> rotate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        RefreshToken stored = repository.findById(TokenHasher.hash(raw)).orElse(null);
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.revoked) {
            revokeAll(stored.userId);
            return Optional.empty();
        }
        if (Instant.now().toEpochMilli() > stored.expiryEpochMillis) {
            return Optional.empty();
        }
        stored.revoked = true;
        repository.save(stored);
        return Optional.of(new Rotation(stored.userId, issue(stored.userId)));
    }

    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        repository.findById(TokenHasher.hash(raw)).ifPresent(token -> {
            token.revoked = true;
            repository.save(token);
        });
    }

    public void revokeAll(String userId) {
        List<RefreshToken> tokens = repository.findByUserId(userId);
        tokens.forEach(token -> token.revoked = true);
        repository.saveAll(tokens);
    }

    @Scheduled(fixedRateString = "${petshop.jwt.refresh-cleaner-rate-ms:3600000}")
    public void purgeExpired() {
        long now = Instant.now().toEpochMilli();
        List<RefreshToken> expired = repository.findAll().stream().filter(t -> t.expiryEpochMillis < now).toList();
        if (!expired.isEmpty()) {
            repository.deleteAll(expired);
        }
    }
}
