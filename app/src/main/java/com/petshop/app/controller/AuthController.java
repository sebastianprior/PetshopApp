package com.petshop.app.controller;

import com.petshop.app.dto.UserDTO;
import com.petshop.app.model.ResetToken;
import com.petshop.app.model.User;
import com.petshop.app.repository.ResetTokenRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.NotificationService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final NotificationService notificationService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final Logger RESET_LOG = LoggerFactory.getLogger("resetTokenLogger");

    public AuthController(JwtUtil jwtUtil, UserRepository userRepository, ResetTokenRepository resetTokenRepository, NotificationService notificationService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> body) {
        String email = body.get("email") != null ? body.get("email").trim() : null;
        String password = body.get("password");

        User u = userRepository.findByEmail(email).orElse(null);
        if (u != null && passwordEncoder.matches(password, u.password)) {
            String token = jwtUtil.generateToken(u.id, u.email, u.role);
            Map<String,Object> resp = new HashMap<>();
            resp.put("token",token);
            resp.put("user",UserDTO.fromUser(u));
            return ResponseEntity.ok(resp);
        }

        return ResponseEntity.status(401).body(Map.of("error","Credenciales inválidas"));
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

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String name = body.getOrDefault("name", "");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error","Faltan campos"));
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error","Usuario ya existe"));
        }

        User u = new User(UUID.randomUUID().toString(), email, passwordEncoder.encode(password), name);
        u.role = "CUSTOMER";
        userRepository.save(u);
        notificationService.notify(u.email, "¡Bienvenido a Petshop, " + u.name + "!");
        String token = jwtUtil.generateToken(u.id, u.email, u.role);
        return ResponseEntity.ok(Map.of("token", token, "user", UserDTO.fromUser(u)));
    }

    @PostMapping("/recover")
    public ResponseEntity<?> recover(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        if (email == null || userRepository.findByEmail(email).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error","Email no registrado"));
        }

        String token = UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + (15 * 60 * 1000);
        ResetToken rt = new ResetToken(token, email, expiry);
        resetTokenRepository.save(rt);

        String msg = "[SIMULATED EMAIL] " + Instant.now() + " | Password reset token for " + email + ": " + token + " (expires in 15 minutes)";
        RESET_LOG.info(msg);

        return ResponseEntity.ok(Map.of("resetToken", token, "expiresInMinutes", 15));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody Map<String,String> body) {
        String token = body.get("token");
        String newPassword = body.get("password");
        if (token == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error","Faltan campos"));
        }

        ResetToken rt = resetTokenRepository.findById(token).orElse(null);
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
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
