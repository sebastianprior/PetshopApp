package com.petshop.app.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "123-abc.apps.googleusercontent.com";
    private static final String KEY_ID = "key-1";

    private KeyPair googleKeys;
    private KeyPair attackerKeys;
    private GoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKeys = generator.generateKeyPair();
        attackerKeys = generator.generateKeyPair();
        verifier = new GoogleTokenVerifier(CLIENT_ID, keyId -> KEY_ID.equals(keyId) ? Optional.of(googleKeys.getPublic()) : Optional.empty());
    }

    private String token(KeyPair signer, String keyId, String audience, String issuer, long expiresInMs, Object emailVerified) {
        var builder = Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("google-user-1")
                .claim("email", "Persona@Gmail.com")
                .claim("name", "Persona Prueba")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs));
        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }
        return builder.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String valid() {
        return token(googleKeys, KEY_ID, CLIENT_ID, "https://accounts.google.com", 60_000, true);
    }

    @Test
    void acceptsATokenSignedByGoogleForThisApp() {
        var identity = verifier.verify(valid());

        assertThat(identity).isPresent();
        assertThat(identity.get().subject()).isEqualTo("google-user-1");
        assertThat(identity.get().email()).isEqualTo("persona@gmail.com"); // se normaliza a minúsculas
        assertThat(identity.get().name()).isEqualTo("Persona Prueba");
    }

    @Test
    void acceptsTheOtherIssuerFormatGoogleUses() {
        assertThat(verifier.verify(token(googleKeys, KEY_ID, CLIENT_ID, "accounts.google.com", 60_000, true))).isPresent();
    }

    @Test
    void acceptsEmailVerifiedAsTheTextTrue() {
        assertThat(verifier.verify(token(googleKeys, KEY_ID, CLIENT_ID, "https://accounts.google.com", 60_000, "true"))).isPresent();
    }

    @Test
    void rejectsATokenMeantForAnotherApplication() {
        assertThat(verifier.verify(token(googleKeys, KEY_ID, "otra-app.apps.googleusercontent.com", "https://accounts.google.com", 60_000, true))).isEmpty();
    }

    @Test
    void rejectsAnExpiredToken() {
        assertThat(verifier.verify(token(googleKeys, KEY_ID, CLIENT_ID, "https://accounts.google.com", -10 * 60_000, true))).isEmpty();
    }

    @Test
    void rejectsATokenNotIssuedByGoogle() {
        assertThat(verifier.verify(token(googleKeys, KEY_ID, CLIENT_ID, "https://evil.example.com", 60_000, true))).isEmpty();
    }

    @Test
    void rejectsAnEmailThatGoogleDidNotVerify() {
        assertThat(verifier.verify(token(googleKeys, KEY_ID, CLIENT_ID, "https://accounts.google.com", 60_000, false))).isEmpty();
        assertThat(verifier.verify(token(googleKeys, KEY_ID, CLIENT_ID, "https://accounts.google.com", 60_000, null))).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithAnotherKeyEvenIfItClaimsGoogleKeyId() {
        assertThat(verifier.verify(token(attackerKeys, KEY_ID, CLIENT_ID, "https://accounts.google.com", 60_000, true))).isEmpty();
    }

    @Test
    void rejectsAnUnknownKeyId() {
        assertThat(verifier.verify(token(googleKeys, "desconocida", CLIENT_ID, "https://accounts.google.com", 60_000, true))).isEmpty();
    }

    @Test
    void rejectsATamperedToken() {
        String[] parts = valid().split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"https://accounts.google.com\",\"aud\":\"%s\",\"sub\":\"x\",\"email\":\"admin@petshop.com\",\"email_verified\":true,\"exp\":9999999999}"
                        .formatted(CLIENT_ID).getBytes(StandardCharsets.UTF_8));

        assertThat(verifier.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithTheSymmetricAlgorithmUsingThePublicKeyAsSecret() {
        // Ataque clásico de "confusión de algoritmo": se firma con HS256 usando la clave pública como si fuera un secreto
        PublicKey publicKey = googleKeys.getPublic();
        SecretKey fakeSecret = Keys.hmacShaKeyFor(((RSAPublicKey) publicKey).getModulus().toByteArray().length >= 32
                ? ((RSAPublicKey) publicKey).getModulus().toByteArray() : new byte[32]);
        String forged = Jwts.builder()
                .header().keyId(KEY_ID).and()
                .issuer("https://accounts.google.com").audience().add(CLIENT_ID).and()
                .subject("x").claim("email", "admin@petshop.com").claim("email_verified", true)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(fakeSecret, Jwts.SIG.HS256).compact();

        assertThat(verifier.verify(forged)).isEmpty();
    }

    @Test
    void rejectsGarbageAndEmptyInput() {
        assertThat(verifier.verify(null)).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
        assertThat(verifier.verify("   ")).isEmpty();
        assertThat(verifier.verify("no-es-un-token")).isEmpty();
        assertThat(verifier.verify("a.b.c")).isEmpty();
        assertThat(verifier.verify("x".repeat(5000))).isEmpty();
    }

    @Test
    void isDisabledWithoutAClientIdAndNeverAcceptsAnything() {
        GoogleTokenVerifier disabled = new GoogleTokenVerifier("  ", keyId -> Optional.of(googleKeys.getPublic()));

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.verify(valid())).isEmpty();
    }

    @Test
    void readsGoogleKeysFromTheirPublishedFormat() throws Exception {
        RSAPublicKey key = (RSAPublicKey) googleKeys.getPublic();
        String json = "{\"keys\":[{\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"kid\":\"abc\",\"n\":\"%s\",\"e\":\"%s\"},{\"kty\":\"EC\",\"kid\":\"ignorada\"}]}"
                .formatted(b64(key.getModulus()), b64(key.getPublicExponent()));

        Map<String, PublicKey> parsed = GoogleJwksKeySource.parse(json);

        assertThat(parsed).containsOnlyKeys("abc");
        assertThat(parsed.get("abc")).isEqualTo(key);
    }

    private static String b64(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
