package com.petshop.app.controller;

import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.model.Return;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.repository.ReturnRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.petshop.app.config.AdminOnly;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final ReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final OrderRepository orderRepository;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;

    public ReturnController(ReturnRepository returnRepository, ProductRepository productRepository,
                             ProductVariantRepository variantRepository, OrderRepository orderRepository,
                             JwtUtil jwtUtil, AdminGuard adminGuard) {
        this.returnRepository = returnRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.orderRepository = orderRepository;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
    }

    /** Unidades que el usuario compró de un producto en ventas que llegaron a buen puerto (sin contar los regalos). */
    private int unidadesCompradas(String userId, String productId) {
        return orderRepository.findByUserIdOrderByFechaDesc(userId).stream()
                .filter(o -> "COMPLETADA".equals(o.estado) || "ENVIADA".equals(o.estado))
                .flatMap(o -> o.items.stream())
                .filter(i -> productId.equals(i.productId) && i.price > 0)
                .mapToInt(i -> i.quantity)
                .sum();
    }

    /** Unidades de ese producto que el usuario ya pidió devolver (las rechazadas no cuentan). */
    private int unidadesYaDevueltas(String userId, String productId) {
        return returnRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .filter(r -> productId.equals(r.productId) && r.estado != Return.Status.RECHAZADA)
                .mapToInt(r -> r.cantidad)
                .sum();
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

        Object variantIdValue = body.get("variantId");
        Long variantId = variantIdValue instanceof Number ? ((Number) variantIdValue).longValue() : null;
        String variant = body.get("variant") != null ? body.get("variant").toString() : null;

        String motivo = body.get("motivo") != null ? body.get("motivo").toString() : "";
        String userId = jwtUtil.extractUserId(token);

        // Solo se puede devolver lo que se compró, y no más unidades de las compradas (descontando lo que ya se pidió devolver)
        int disponibles = unidadesCompradas(userId, productId) - unidadesYaDevueltas(userId, productId);
        if (disponibles < 1) {
            return ResponseEntity.status(422).body(Map.of("error", "No compraste este producto o ya pediste devolver todas las unidades"));
        }
        if (cantidad > disponibles) {
            return ResponseEntity.status(422).body(Map.of("error", "Solo podés devolver " + disponibles + " unidad(es) de este producto"));
        }

        Return devolucion = new Return(userId, productId, cantidad, motivo, Return.Status.PENDIENTE, Instant.now());
        devolucion.variantId = variantId;
        devolucion.variant = variant;
        returnRepository.save(devolucion);
        return ResponseEntity.ok(devolucion);
    }

    @ApiResponse(responseCode = "200", description = "Mis devoluciones", content = @Content(array = @ArraySchema(schema = @Schema(implementation = com.petshop.app.model.Return.class))))
    @GetMapping("/me")
    public ResponseEntity<?> listMine(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String userId = jwtUtil.extractUserId(token);
        return ResponseEntity.ok(returnRepository.findByUserIdOrderByRequestedAtDesc(userId));
    }

    @ApiResponse(responseCode = "200", description = "Todas las devoluciones", content = @Content(array = @ArraySchema(schema = @Schema(implementation = com.petshop.app.model.Return.class))))
    @AdminOnly
    @GetMapping
    public ResponseEntity<?> listAll(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        return ResponseEntity.ok(returnRepository.findAllByOrderByRequestedAtDesc());
    }

    @AdminOnly
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
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

        if (devolucion.estado == Return.Status.APROBADA) {
            restockReturnedItem(devolucion);
        }

        returnRepository.save(devolucion);
        return ResponseEntity.ok(devolucion);
    }

    private void restockReturnedItem(Return devolucion) {
        // Atómico: leer, sumar y reescribir pisaba las compras que ocurrían al mismo tiempo
        if (productRepository.liberar(devolucion.productId, devolucion.cantidad) == 0) {
            return;
        }
        if (devolucion.variantId != null) {
            variantRepository.liberar(devolucion.variantId, devolucion.cantidad);
        }
    }
}
