package com.petshop.app.controller;

import com.petshop.app.model.Return;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ReturnRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final ReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;

    public ReturnController(ReturnRepository returnRepository, ProductRepository productRepository,
                             JwtUtil jwtUtil, AdminGuard adminGuard) {
        this.returnRepository = returnRepository;
        this.productRepository = productRepository;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
    }

    @PostMapping
    public ResponseEntity<?> request(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                      @RequestBody Map<String, Object> body) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String productId = (String) body.get("productId");
        if (productId == null || productId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto inválido"));
        }

        if (!productRepository.existsById(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }

        Object cantidadValue = body.get("cantidad");
        if (!(cantidadValue instanceof Number) || ((Number) cantidadValue).intValue() < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cantidad inválida"));
        }
        int cantidad = ((Number) cantidadValue).intValue();

        String motivo = body.get("motivo") != null ? body.get("motivo").toString() : "";
        String userId = jwtUtil.extractUserId(token);

        Return devolucion = new Return(userId, productId, cantidad, motivo, Return.Status.PENDIENTE, Instant.now());
        returnRepository.save(devolucion);
        return ResponseEntity.ok(devolucion);
    }

    @GetMapping("/me")
    public ResponseEntity<?> listMine(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String userId = jwtUtil.extractUserId(token);
        return ResponseEntity.ok(returnRepository.findByUserIdOrderByRequestedAtDesc(userId));
    }

    @GetMapping
    public ResponseEntity<?> listAll(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        return ResponseEntity.ok(returnRepository.findAllByOrderByRequestedAtDesc());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        Return devolucion = returnRepository.findById(id).orElse(null);
        if (devolucion == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Devolución no encontrada"));
        }

        String nuevoEstado = body.get("estado");
        if (!"APROBADA".equals(nuevoEstado) && !"RECHAZADA".equals(nuevoEstado)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Estado inválido, debe ser APROBADA o RECHAZADA"));
        }

        if (devolucion.estado != Return.Status.PENDIENTE) {
            return ResponseEntity.status(409).body(Map.of("error", "La devolución ya fue resuelta"));
        }

        devolucion.estado = Return.Status.valueOf(nuevoEstado);
        returnRepository.save(devolucion);
        return ResponseEntity.ok(devolucion);
    }
}
