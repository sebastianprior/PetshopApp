package com.petshop.app.controller;

import com.petshop.app.model.User;
import com.petshop.app.repository.ResetTokenRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.LoginRateLimiter;
import com.petshop.app.service.NotificationService;
import com.petshop.app.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private UserRepository userRepository;
    private RefreshTokenService refreshTokenService;
    private JwtUtil jwtUtil;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        refreshTokenService = mock(RefreshTokenService.class);
        ResetTokenRepository resetTokenRepository = mock(ResetTokenRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        LoginRateLimiter loginRateLimiter = new LoginRateLimiter();
        controller = new AuthController(jwtUtil, userRepository, resetTokenRepository, notificationService, loginRateLimiter,
                refreshTokenService);
    }

    @Test
    void disabledAccountCannotLoginEvenWithCorrectPassword() {
        User user = new User("user-1", "cliente@example.com", ENCODER.encode("password123"), "Cliente");
        user.active = false;
        when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.login(Map.of("email", "cliente@example.com", "password", "password123"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void activeAccountCanLoginWithCorrectPassword() {
        User user = new User("user-1", "cliente@example.com", ENCODER.encode("password123"), "Cliente");
        when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.login(Map.of("email", "cliente@example.com", "password", "password123"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void loginReturnsAnAccessTokenAndARefreshToken() {
        User user = new User("user-1", "cliente@example.com", ENCODER.encode("password123"), "Cliente");
        when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenService.issue("user-1")).thenReturn("refresh-ficticio");

        Map<?, ?> body = (Map<?, ?>) controller.login(Map.of("email", "cliente@example.com", "password", "password123")).getBody();

        assertThat(jwtUtil.isTokenValid((String) body.get("token"))).isTrue();
        assertThat(body.get("refreshToken")).isEqualTo("refresh-ficticio");
    }

    @Test
    void refreshIssuesNewTokensWithTheCurrentRoleFromTheDatabase() {
        User user = new User("user-1", "cliente@example.com", "hash", "Cliente");
        user.role = "ADMIN";
        when(refreshTokenService.rotate("viejo")).thenReturn(Optional.of(new RefreshTokenService.Rotation("user-1", "nuevo")));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.refresh(Map.of("refreshToken", "viejo"));

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body.get("refreshToken")).isEqualTo("nuevo");
        assertThat(jwtUtil.extractRole((String) body.get("token"))).isEqualTo("ADMIN");
    }

    @Test
    void refreshIsRejectedForUnknownTokensAndForDisabledUsers() {
        when(refreshTokenService.rotate("desconocido")).thenReturn(Optional.empty());
        assertThat(controller.refresh(Map.of("refreshToken", "desconocido")).getStatusCode().value()).isEqualTo(401);

        User disabled = new User("user-2", "baja@example.com", "hash", "Baja");
        disabled.active = false;
        when(refreshTokenService.rotate("de-baja")).thenReturn(Optional.of(new RefreshTokenService.Rotation("user-2", "nuevo")));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(disabled));

        assertThat(controller.refresh(Map.of("refreshToken", "de-baja")).getStatusCode().value()).isEqualTo(401);
        verify(refreshTokenService).revokeAll("user-2");
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        controller.logout(Map.of("refreshToken", "sesion-actual"));

        verify(refreshTokenService).revoke("sesion-actual");
    }
}
