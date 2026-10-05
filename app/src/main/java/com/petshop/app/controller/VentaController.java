package com.petshop.app.controller;

import com.petshop.app.dto.VentaDTO;
import com.petshop.app.dto.VentaRequest;
import com.petshop.app.model.Order;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.VentaService;
import com.petshop.app.service.VentaService.VentaOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ventas")
@Tag(name = "Ventas", description = "Checkout, estado del pago y cancelación")
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
    @Operation(summary = "Crear una venta",
            description = "Toma el carrito del usuario (o invitado), reserva el stock y autoriza el pago. "
                    + "Exige el header Idempotency-Key: repetir la misma clave y el mismo cuerpo devuelve la misma venta.")
    @ApiResponse(responseCode = "201", description = "Pago aprobado, venta COMPLETADA")
    @ApiResponse(responseCode = "202", description = "La pasarela no respondió a tiempo: venta PAGO_PENDIENTE (se confirma o cancela sola)")
    @ApiResponse(responseCode = "400", description = "Falta el header Idempotency-Key")
    @ApiResponse(responseCode = "401", description = "Sin token ni X-Guest-Id")
    @ApiResponse(responseCode = "402", description = "Pago rechazado (el stock se libera)")
    @ApiResponse(responseCode = "403", description = "Los administradores no compran")
    @ApiResponse(responseCode = "409", description = "Stock insuficiente o Idempotency-Key reutilizada con otro cuerpo")
    @ApiResponse(responseCode = "422", description = "Carrito vacío, datos de envío incompletos o datos de pago inválidos")
    public ResponseEntity<?> crear(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                    @Parameter(description = "Identificador de invitado, usado cuando no hay token") @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @RequestBody(required = false) VentaRequest request) {
        String principal = principal(token, guestId);
        if (principal == null) {
            return Problems.unauthorized();
        }
        if (authenticated(token) && adminGuard.isAdmin(token)) {
            return Problems.forbidden("Los administradores no pueden realizar compras.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Problems.of(400, "idempotency-key-requerida", "Idempotency-Key requerida",
                    "El POST /ventas exige el header Idempotency-Key.");
        }

        VentaOutcome outcome = ventaService.crear(principal, idempotencyKey.trim(),
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
    @Operation(summary = "Listar ventas", description = "Las propias del usuario; el administrador ve todas.")
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
    @Operation(summary = "Obtener una venta")
    public ResponseEntity<?> obtener(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                      @Parameter(description = "Identificador de invitado, usado cuando no hay token") @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                      @PathVariable Long id) {
        String principal = principal(token, guestId);
        if (principal == null) {
            return Problems.unauthorized();
        }
        Order order = visibleOrder(token, principal, id);
        if (order == null) {
            return Problems.notFound("No existe la venta " + id + ".");
        }
        return ResponseEntity.ok(ventaService.toDto(order));
    }

    @GetMapping("/{id}/pago")
    @Operation(summary = "Consultar el estado del pago", description = "Sirve para hacer polling cuando la venta quedó PAGO_PENDIENTE.")
    public ResponseEntity<?> pago(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                   @Parameter(description = "Identificador de invitado, usado cuando no hay token") @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                   @PathVariable Long id) {
        String principal = principal(token, guestId);
        if (principal == null) {
            return Problems.unauthorized();
        }
        Order order = visibleOrder(token, principal, id);
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
    @Operation(summary = "Cancelar una venta", description = "Libera el stock; si ya estaba paga, el pago pasa a REEMBOLSADO.")
    public ResponseEntity<?> cancelar(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                       @Parameter(description = "Identificador de invitado, usado cuando no hay token") @RequestHeader(value = "X-Guest-Id", required = false) String guestId,
                                       @PathVariable Long id) {
        String principal = principal(token, guestId);
        if (principal == null) {
            return Problems.unauthorized();
        }
        Order order = visibleOrder(token, principal, id);
        if (order == null) {
            return Problems.notFound("No existe la venta " + id + ".");
        }
        return respond(ventaService.cancelarPorSolicitud(order));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cambiar el estado de una venta (solo administrador)")
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

    /** Usuario autenticado (id del JWT) o, si no hay token válido, el invitado ("guest:<id>"); null si ninguno. */
    private String principal(String token, String guestId) {
        if (authenticated(token)) {
            return jwtUtil.extractUserId(token);
        }
        return guestId != null && !guestId.isBlank() ? "guest:" + guestId.trim() : null;
    }

    private Order visibleOrder(String token, String principal, Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return null;
        }
        boolean owner = principal.equals(order.userId);
        return owner || (authenticated(token) && adminGuard.isAdmin(token)) ? order : null;
    }
}
