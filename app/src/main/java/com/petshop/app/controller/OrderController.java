package com.petshop.app.controller;

import com.petshop.app.model.Order;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;

    public OrderController(OrderRepository orderRepository, JwtUtil jwtUtil, AdminGuard adminGuard) {
        this.orderRepository = orderRepository;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/me")
    public ResponseEntity<?> listMine(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String userId = jwtUtil.extractUserId(token);
        return ResponseEntity.ok(orderRepository.findByUserIdOrderByFechaDesc(userId));
    }

    @GetMapping
    public ResponseEntity<?> listAll(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        return ResponseEntity.ok(orderRepository.findAllByOrderByFechaDesc());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Orden no encontrada"));
        }

        String nuevoEstado = body.get("estado");
        if (nuevoEstado == null || nuevoEstado.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Estado inválido"));
        }

        order.estado = nuevoEstado;
        orderRepository.save(order);
        return ResponseEntity.ok(order);
    }
}
