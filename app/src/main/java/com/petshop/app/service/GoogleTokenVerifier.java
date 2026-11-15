package com.petshop.app.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.PublicKey;
import java.util.Optional;
import java.util.Set;

/**
 * Comprueba que un "ID token" de Google es auténtico: firma RS256 con las claves públicas de Google, que sea para
 * esta aplicación (audience = nuestro Client ID), que lo haya emitido Google, que no esté vencido y que el email
 * esté verificado. No se confía en nada de lo que mande el navegador sin pasar por acá.
 */
@Component
public class GoogleTokenVerifier {

    /** Quién es la persona según Google. */
    public record GoogleIdentity(String subject, String email, String name) {}

    /** De dónde salen las claves públicas de Google (en tests se reemplaza por claves propias). */
    public interface KeySource {
        Optional<PublicKey> keyFor(String keyId);
    }

    private static final Logger LOG = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final String clientId;
    private final KeySource keySource;

    public GoogleTokenVerifier(@Value("${petshop.google.client-id:}") String clientId, KeySource keySource) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.keySource = keySource;
    }

    /** ¿Está configurado el inicio de sesión con Google? */
    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    public String clientId() {
        return clientId;
    }

    /** La identidad de Google si el token es válido; vacío en cualquier otro caso (firma, audiencia, vencimiento, email sin verificar…). */
    public Optional<GoogleIdentity> verify(String idToken) {
        if (!isEnabled() || idToken == null || idToken.isBlank() || idToken.length() > 4096) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            return keySource.keyFor(header.getKeyId()).orElse(null);
                        }
                    })
                    .requireAudience(clientId)
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(idToken);

            // Google firma con RS256: se rechaza cualquier otro algoritmo (evita que se disfrace un token firmado con otra clase de clave)
            if (!"RS256".equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            Claims claims = jws.getPayload();
            if (!ISSUERS.contains(claims.getIssuer()) || claims.getExpiration() == null) {
                return Optional.empty();
            }
            String email = claims.get("email", String.class);
            Object verified = claims.get("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified));
            if (email == null || email.isBlank() || !emailVerified || claims.getSubject() == null || claims.getSubject().isBlank()) {
                return Optional.empty();
            }
            String name = claims.get("name", String.class);
            return Optional.of(new GoogleIdentity(claims.getSubject(), email.trim().toLowerCase(), name == null ? "" : name.trim()));
        } catch (JwtException | IllegalArgumentException e) {
            LOG.debug("Token de Google rechazado: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
