package com.petshop.app.controller;

import com.petshop.app.dto.UserDTO;
import com.petshop.app.model.User;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.RefreshTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;
    private final RefreshTokenService refreshTokenService;

    public UserController(UserRepository userRepository, JwtUtil jwtUtil, AdminGuard adminGuard,
                          RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        List<UserDTO> users = userRepository.findAll().stream().map(UserDTO::fromUser).toList();
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                         @PathVariable String id,
                                         @RequestBody Map<String, String> body) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        String nuevoRol = body.get("role");
        if (!"ADMIN".equals(nuevoRol) && !"CUSTOMER".equals(nuevoRol)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El rol debe ser ADMIN o CUSTOMER"));
        }

        if (id.equals(jwtUtil.extractUserId(token))) {
            return ResponseEntity.badRequest().body(Map.of("error", "No podés cambiar tu propio rol"));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Usuario no encontrado"));
        }

        user.role = nuevoRol;
        userRepository.save(user);
        // Fuerza a renovar la sesión: el nuevo rol se aplica en cuanto vence el access token (máx. 15 min).
        refreshTokenService.revokeAll(user.id);
        return ResponseEntity.ok(UserDTO.fromUser(user));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                           @PathVariable String id,
                                           @RequestBody Map<String, Boolean> body) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta el campo 'active'"));
        }

        if (id.equals(jwtUtil.extractUserId(token))) {
            return ResponseEntity.badRequest().body(Map.of("error", "No podés deshabilitar tu propia cuenta"));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Usuario no encontrado"));
        }

        user.active = active;
        userRepository.save(user);
        if (!active) {
            refreshTokenService.revokeAll(user.id);
        }
        return ResponseEntity.ok(UserDTO.fromUser(user));
    }
}
