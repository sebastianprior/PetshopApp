package com.petshop.app.controller;

import com.petshop.app.dto.VentaDTO;
import com.petshop.app.dto.VentaRequest;
import com.petshop.app.model.Order;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.VentaService;
import com.petshop.app.service.VentaService.VentaOutcome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final OrderRepository orderRepository;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;

    public VentaController(VentaService ventaService, OrderRepository orderRepository,
                           JwtUtil jwtUtil, AdminGuard adminGuard) {
        this.ventaService = ventaService;
        this.orderRepository = orderRepository;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @RequestBody(required = false) VentaRequest request) {
        if (!authenticated(token)) {
            return Problems.unauthorized();
        }
        if (adminGuard.isAdmin(token)) {
            return Problems.forbidden("Los administradores no pueden realizar compras.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Problems.of(400, "idempotency-key-requerida", "Idempotency-Key requerida",
                    "El POST /ventas exige el header Idempotency-Key.");
        }

        VentaOutcome outcome = ventaService.crear(jwtUtil.extractUserId(token), idempotencyKey.trim(),
                request != null ? request : new VentaRequest());
        if (outcome.isProblem()) {
            return Problems.of(outcome.status(), outcome.problemSlug(), outcome.problemTitle(), outcome.problemDetail());
        }

        VentaDTO body = ventaService.toDto(outcome.order());
        URI location = URI.create("/api/v1/ventas/" + outcome.order().id);
        return outcome.status() == 201
                ? ResponseEntity.created(location).body(body)
                : ResponseEntity.accepted().location(location).body(body);
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!authenticated(token)) {
            return Problems.unauthorized();
        }
        List<Order> orders = adminGuard.isAdmin(token)
                ? orderRepository.findAllByOrderByFechaDesc()
                : orderRepository.findByUserIdOrderByFechaDesc(jwtUtil.extractUserId(token));
        return ResponseEntity.ok(orders.stream().map(ventaService::toDto).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                      @PathVariable Long id) {
        if (!authenticated(token)) {
            return Problems.unauthorized();
        }
        Order order = visibleOrder(token, id);
        if (order == null) {
            return Problems.notFound("No existe la venta " + id + ".");
        }
        return ResponseEntity.ok(ventaService.toDto(order));
    }

    @GetMapping("/{id}/pago")
    public ResponseEntity<?> pago(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                   @PathVariable Long id) {
        if (!authenticated(token)) {
            return Problems.unauthorized();
        }
        Order order = visibleOrder(token, id);
        if (order == null) {
            return Problems.notFound("No existe la venta " + id + ".");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ventaId", order.id);
        body.put("idTransaccion", order.idTransaccion);
        body.put("estadoPago", order.estadoPago);
        body.put("estadoVenta", order.estado);
        body.put("intentos", order.intentosPago);
        body.put("monto", order.total);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/cancelacion")
    public ResponseEntity<?> cancelar(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                       @PathVariable Long id) {
        if (!authenticated(token)) {
            return Problems.unauthorized();
        }
        Order order = visibleOrder(token, id);
        if (order == null) {
            return Problems.notFound("No existe la venta " + id + ".");
        }
        return respond(ventaService.cancelarPorSolicitud(order));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> cambiarEstado(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                            @PathVariable Long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        if (!authenticated(token)) {
            return Problems.unauthorized();
        }
        if (!adminGuard.isAdmin(token)) {
            return Problems.forbidden("Requiere permisos de administrador.");
        }
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return Problems.notFound("No existe la venta " + id + ".");
        }
        String estado = body != null ? body.get("estado") : null;
        if (estado == null || estado.isBlank()) {
            return Problems.of(400, "estado-requerido", "Estado requerido", "Falta el campo 'estado'.");
        }
        return respond(ventaService.cambiarEstado(order, estado));
    }

    private ResponseEntity<?> respond(VentaOutcome outcome) {
        if (outcome.isProblem()) {
            return Problems.of(outcome.status(), outcome.problemSlug(), outcome.problemTitle(), outcome.problemDetail());
        }
        return ResponseEntity.ok(ventaService.toDto(outcome.order()));
    }

    private boolean authenticated(String token) {
        return token != null && jwtUtil.isTokenValid(token);
    }

    private Order visibleOrder(String token, Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return null;
        }
        boolean owner = order.userId != null && order.userId.equals(jwtUtil.extractUserId(token));
        return owner || adminGuard.isAdmin(token) ? order : null;
    }
}
