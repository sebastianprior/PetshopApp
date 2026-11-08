package com.petshop.app.controller;

import com.petshop.app.model.User;
import com.petshop.app.repository.ResetTokenRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.GoogleTokenVerifier;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.LoginRateLimiter;
import com.petshop.app.service.NotificationService;
import com.petshop.app.service.RefreshTokenService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleLoginTest {

    private static final String CLIENT_ID = "123-abc.apps.googleusercontent.com";

    private UserRepository userRepository;
    private NotificationService notificationService;
    private KeyPair googleKeys;
    private AuthController controller;
    private AuthController controllerWithoutGoogle;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKeys = generator.generateKeyPair();

        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        when(refreshTokens.issue(anyString())).thenReturn("refresh-token");
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        ResetTokenRepository resets = mock(ResetTokenRepository.class);

        controller = new AuthController(jwtUtil, userRepository, resets, notificationService, new LoginRateLimiter(), refreshTokens,
                new GoogleTokenVerifier(CLIENT_ID, keyId -> Optional.of(googleKeys.getPublic())));
        controllerWithoutGoogle = new AuthController(jwtUtil, userRepository, resets, notificationService, new LoginRateLimiter(), refreshTokens,
                new GoogleTokenVerifier("", keyId -> Optional.empty()));
    }

    private String googleToken(String email, String name) {
        return Jwts.builder()
                .header().keyId("k").and()
                .issuer("https://accounts.google.com")
                .audience().add(CLIENT_ID).and()
                .subject("sub-1")
                .claim("email", email)
                .claim("email_verified", true)
                .claim("name", name)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(googleKeys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    void exposesTheClientIdOnlyWhenGoogleIsConfigured() {
        assertThat(controller.config().get("googleClientId")).isEqualTo(CLIENT_ID);
        assertThat(controllerWithoutGoogle.config().get("googleClientId")).isNull();
    }

    @Test
    void aNewPersonGetsACustomerAccountAndASession() {
        when(userRepository.findByEmail("nueva@gmail.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.google(Map.of("credential", googleToken("Nueva@Gmail.com", "Nueva Persona")));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body(response)).containsKeys("token", "refreshToken", "user");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().email).isEqualTo("nueva@gmail.com");
        assertThat(saved.getValue().name).isEqualTo("Nueva Persona");
        assertThat(saved.getValue().role).isEqualTo("CUSTOMER");
        assertThat(saved.getValue().authProvider).isEqualTo("GOOGLE");
        // la contraseña es una al azar: no coincide con ninguna que alguien pueda escribir
        assertThat(new BCryptPasswordEncoder().matches("", saved.getValue().password)).isFalse();
        verify(notificationService).notify(anyString(), anyString());
    }

    @Test
    void usesThePartBeforeTheAtWhenGoogleSendsNoName() {
        when(userRepository.findByEmail("sinnombre@gmail.com")).thenReturn(Optional.empty());

        controller.google(Map.of("credential", googleToken("sinnombre@gmail.com", "")));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().name).isEqualTo("sinnombre");
    }

    @Test
    void anExistingGoogleAccountJustSignsIn() {
        User existing = new User("u1", "vuelve@gmail.com", "hash", "Vuelve");
        existing.authProvider = "GOOGLE";
        when(userRepository.findByEmail("vuelve@gmail.com")).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = controller.google(Map.of("credential", googleToken("vuelve@gmail.com", "Vuelve")));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(userRepository, never()).save(any());
    }

    @Test
    void anAccountCreatedWithAPasswordIsNeverTakenOverThroughGoogle() {
        User admin = new User("a1", "admin@petshop.com", "hash", "Admin");
        admin.role = "ADMIN";
        when(userRepository.findByEmail("admin@petshop.com")).thenReturn(Optional.of(admin));

        ResponseEntity<?> response = controller.google(Map.of("credential", googleToken("admin@petshop.com", "Alguien")));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(body(response)).doesNotContainKey("token");
        verify(userRepository, never()).save(any());
    }

    @Test
    void aDisabledGoogleAccountCannotSignIn() {
        User disabled = new User("u2", "baja@gmail.com", "hash", "Baja");
        disabled.authProvider = "GOOGLE";
        disabled.active = false;
        when(userRepository.findByEmail("baja@gmail.com")).thenReturn(Optional.of(disabled));

        ResponseEntity<?> response = controller.google(Map.of("credential", googleToken("baja@gmail.com", "Baja")));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void anInvalidOrMissingTokenIsRejectedAndNothingIsCreated() {
        assertThat(controller.google(Map.of("credential", "falso")).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.google(Map.of()).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.google(null).getStatusCode().value()).isEqualTo(401);
        verify(userRepository, never()).save(any());
    }

    @Test
    void whenGoogleIsNotConfiguredTheEndpointIsNotAvailable() {
        ResponseEntity<?> response = controllerWithoutGoogle.google(Map.of("credential", googleToken("x@gmail.com", "X")));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(userRepository, never()).save(any());
    }

    @Test
    void theSessionOfAGoogleUserNeverCarriesAnAdminRole() {
        when(userRepository.findByEmail("cliente@gmail.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.google(Map.of("credential", googleToken("cliente@gmail.com", "Cliente")));

        Object user = body(response).get("user");
        assertThat(user.toString()).doesNotContain("ADMIN");
    }
}
