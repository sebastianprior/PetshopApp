package com.petshop.app.controller;

import com.petshop.app.dto.RegisterRequest;
import com.petshop.app.dto.UserDTO;
import com.petshop.app.model.ResetToken;
import com.petshop.app.model.User;
import com.petshop.app.repository.ResetTokenRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.GoogleTokenVerifier;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.LoginRateLimiter;
import com.petshop.app.service.NotificationService;
import com.petshop.app.service.PasswordPolicy;
import com.petshop.app.service.RefreshTokenService;
import com.petshop.app.service.TokenHasher;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final long RESET_TOKEN_MINUTES = 15;
    private static final String GOOGLE = "GOOGLE";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final NotificationService notificationService;
    private final LoginRateLimiter loginRateLimiter;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(JwtUtil jwtUtil, UserRepository userRepository, ResetTokenRepository resetTokenRepository,
                           NotificationService notificationService, LoginRateLimiter loginRateLimiter,
                           RefreshTokenService refreshTokenService, GoogleTokenVerifier googleTokenVerifier) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.notificationService = notificationService;
        this.loginRateLimiter = loginRateLimiter;
        this.refreshTokenService = refreshTokenService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    /** Qué métodos de ingreso ofrece la tienda (el Client ID de Google es público: lo necesita el botón del navegador). */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        config.put("googleClientId", googleTokenVerifier.isEnabled() ? googleTokenVerifier.clientId() : null);
        return config;
    }

    /**
     * Ingreso con Google. El navegador manda el token que le dio Google y acá se verifica su firma: nunca se confía en el
     * email que diga el navegador. Si la persona no existe se crea una cuenta de cliente; si ya existe una cuenta con
     * ese email creada con contraseña no se mezclan (para que nadie pueda adueñarse de una cuenta ajena).
     */
    @PostMapping("/google")
    public ResponseEntity<?> google(@RequestBody Map<String, String> body) {
        if (!googleTokenVerifier.isEnabled()) {
            return ResponseEntity.status(404).body(Map.of("error", "El ingreso con Google no está disponible."));
        }
        String credential = body == null ? null : body.get("credential");
        var identity = googleTokenVerifier.verify(credential);
        if (identity.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "No pudimos validar tu cuenta de Google. Probá de nuevo."));
        }

        String email = identity.get().email();
        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            if (!existing.active) {
                return ResponseEntity.status(403).body(Map.of("error", "Tu cuenta fue deshabilitada. Contactá a soporte."));
            }
            if (!GOOGLE.equals(existing.authProvider)) {
                return ResponseEntity.status(409).body(Map.of("error",
                        "Ya existe una cuenta con ese email creada con contraseña. Ingresá con tu contraseña o recuperala desde \"Olvidé mi contraseña\"."));
            }
            return ResponseEntity.ok(session(existing));
        }

        String name = identity.get().name().isBlank() ? email.substring(0, email.indexOf('@')) : identity.get().name();
        // La cuenta de Google no tiene contraseña propia: se guarda una al azar que nadie conoce
        User created = new User(UUID.randomUUID().toString(), email, passwordEncoder.encode(UUID.randomUUID().toString()), name);
        created.role = "CUSTOMER";
        created.authProvider = GOOGLE;
        userRepository.save(created);
        notificationService.notify(created.email, "¡Bienvenido a Petshop, " + created.name + "!");
        return ResponseEntity.ok(session(created));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> body) {
        String email = body.get("email") != null ? body.get("email").trim() : null;
        String password = body.get("password");

        long lockedSeconds = loginRateLimiter.secondsLockedOut(email);
        if (lockedSeconds > 0) {
            long minutes = (lockedSeconds + 59) / 60;
            return ResponseEntity.status(429).body(Map.of("error",
                    "Demasiados intentos fallidos. Probá de nuevo en " + minutes + " minuto(s)."));
        }

        User u = userRepository.findByEmail(email).orElse(null);
        if (u != null && !u.active) {
            return ResponseEntity.status(403).body(Map.of("error", "Tu cuenta fue deshabilitada. Contactá a soporte."));
        }

        if (u != null && passwordEncoder.matches(password, u.password)) {
            loginRateLimiter.recordSuccess(email);
            return ResponseEntity.ok(session(u));
        }

        loginRateLimiter.recordFailure(email);
        return ResponseEntity.status(401).body(Map.of("error","Credenciales inválidas"));
    }

    /** Access token de 15 minutos + refresh token rotativo + datos públicos del usuario. */
    private Map<String, Object> session(User u) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", jwtUtil.generateToken(u.id, u.email, u.role));
        resp.put("refreshToken", refreshTokenService.issue(u.id));
        resp.put("user", UserDTO.fromUser(u));
        return resp;
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        var rotation = refreshTokenService.rotate(body.get("refreshToken"));
        if (rotation.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Sesión expirada"));
        }

        User u = userRepository.findById(rotation.get().userId()).orElse(null);
        if (u == null || !u.active) {
            refreshTokenService.revokeAll(rotation.get().userId());
            return ResponseEntity.status(401).body(Map.of("error", "Sesión expirada"));
        }

        // El rol y el estado se leen de la base en cada renovación: un cambio de rol o una baja se aplican en minutos.
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", jwtUtil.generateToken(u.id, u.email, u.role));
        resp.put("refreshToken", rotation.get().refreshToken());
        resp.put("user", UserDTO.fromUser(u));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> body) {
        refreshTokenService.revoke(body != null ? body.get("refreshToken") : null);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (token != null) {
            try {
                String userId = jwtUtil.extractUserId(token);
                User u = userRepository.findById(userId).orElse(null);
                if (u != null) {
                    return ResponseEntity.ok(UserDTO.fromUser(u));
                }
            } catch (JwtException | IllegalArgumentException e) {
                // falls through to 401 below
            }
        }
        return ResponseEntity.status(401).body(Map.of("error","No autorizado"));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                       @RequestBody Map<String, String> body) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede estar vacío"));
        }

        User u = userRepository.findById(jwtUtil.extractUserId(token)).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        u.name = name.trim();
        userRepository.save(u);
        return ResponseEntity.ok(UserDTO.fromUser(u));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                             @RequestBody Map<String, String> body) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");
        if (currentPassword == null || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Faltan campos"));
        }
        String policyError = PasswordPolicy.validate(newPassword);
        if (policyError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", policyError));
        }

        User u = userRepository.findById(jwtUtil.extractUserId(token)).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        if (!passwordEncoder.matches(currentPassword, u.password)) {
            return ResponseEntity.status(401).body(Map.of("error", "La contraseña actual no es correcta"));
        }

        u.password = passwordEncoder.encode(newPassword);
        userRepository.save(u);
        // Se cierran las demás sesiones; la actual sigue con un refresh token nuevo.
        refreshTokenService.revokeAll(u.id);
        return ResponseEntity.ok(Map.of("ok", true, "refreshToken", refreshTokenService.issue(u.id)));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest body) {
        String email = body.email.trim();
        String policyError = PasswordPolicy.validate(body.password);
        if (policyError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", policyError));
        }
        String name = body.name != null ? body.name : "";

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error","Usuario ya existe"));
        }

        User u = new User(UUID.randomUUID().toString(), email, passwordEncoder.encode(body.password), name);
        u.role = "CUSTOMER";
        userRepository.save(u);
        notificationService.notify(u.email, "¡Bienvenido a Petshop, " + u.name + "!");
        return ResponseEntity.ok(session(u));
    }

    /**
     * Pide un código de recuperación. La respuesta es siempre la misma (exista o no el email) y nunca incluye el código:
     * solo llega por el canal de notificación (email). En la base se guarda únicamente su hash.
     */
    @PostMapping("/recover")
    public ResponseEntity<?> recover(@RequestBody Map<String,String> body) {
        String email = body.get("email") != null ? body.get("email").trim() : null;

        if (email != null && !email.isEmpty()) {
            userRepository.findByEmail(email).ifPresent(user -> {
                String token = TokenHasher.newToken();
                long expiry = System.currentTimeMillis() + (RESET_TOKEN_MINUTES * 60 * 1000);
                resetTokenRepository.save(new ResetToken(TokenHasher.hash(token), user.email, expiry));
                notificationService.sendSecret(user.email, "Recuperación de contraseña",
                        "Tu código para restablecer la contraseña es: " + token
                                + "\nVence en " + RESET_TOKEN_MINUTES + " minutos. Si no lo pediste, ignorá este mensaje.");
            });
        }

        return ResponseEntity.ok(Map.of("ok", true, "expiresInMinutes", RESET_TOKEN_MINUTES,
                "message", "Si el email está registrado, te enviamos un código para restablecer la contraseña."));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody Map<String,String> body) {
        String token = body.get("token");
        String newPassword = body.get("password");
        if (token == null || token.isBlank() || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error","Faltan campos"));
        }
        String policyError = PasswordPolicy.validate(newPassword);
        if (policyError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", policyError));
        }

        ResetToken rt = resetTokenRepository.findById(TokenHasher.hash(token.trim())).orElse(null);
        if (rt == null) {
            return ResponseEntity.badRequest().body(Map.of("error","Token inválido"));
        }

        if (rt.isExpired()) {
            resetTokenRepository.delete(rt);
            return ResponseEntity.badRequest().body(Map.of("error","Token expirado"));
        }

        String email = rt.email;
        User u = userRepository.findByEmail(email).orElse(null);
        if (u == null) {
            return ResponseEntity.badRequest().body(Map.of("error","Usuario no encontrado"));
        }

        u.password = passwordEncoder.encode(newPassword);
        userRepository.save(u);
        resetTokenRepository.delete(rt);
        refreshTokenService.revokeAll(u.id);
        loginRateLimiter.recordSuccess(u.email);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
