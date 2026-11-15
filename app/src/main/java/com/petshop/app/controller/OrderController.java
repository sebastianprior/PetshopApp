package com.petshop.app.controller;

import com.petshop.app.dto.OrderDTO;
import com.petshop.app.dto.OrderStatsDTO;
import com.petshop.app.dto.TopProductStatDTO;
import com.petshop.app.model.Order;
import com.petshop.app.model.Product;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.VentaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.petshop.app.config.AdminOnly;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;
    private final VentaService ventaService;

    public OrderController(OrderRepository orderRepository, ProductRepository productRepository,
                            JwtUtil jwtUtil, AdminGuard adminGuard, VentaService ventaService) {
        this.ventaService = ventaService;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
    }

    @ApiResponse(responseCode = "200", description = "Mis pedidos", content = @Content(array = @ArraySchema(schema = @Schema(implementation = com.petshop.app.dto.OrderDTO.class))))
    @GetMapping("/me")
    public ResponseEntity<?> listMine(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String userId = jwtUtil.extractUserId(token);
        return ResponseEntity.ok(orderRepository.findByUserIdOrderByFechaDesc(userId).stream().map(OrderDTO::forCustomer).toList());
    }

    @ApiResponse(responseCode = "200", description = "Todos los pedidos", content = @Content(array = @ArraySchema(schema = @Schema(implementation = com.petshop.app.dto.OrderDTO.class))))
    @AdminOnly
    @GetMapping
    public ResponseEntity<?> listAll(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        return ResponseEntity.ok(orderRepository.findAllByOrderByFechaDesc().stream().map(OrderDTO::forAdmin).toList());
    }

    @AdminOnly
    @GetMapping("/stats")
    public ResponseEntity<?> stats(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        List<Order> orders = orderRepository.findAll();

        long totalOrders = orders.size();
        double totalRevenue = orders.stream().mapToDouble(o -> o.total).sum();

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long ordersToday = orders.stream()
                .filter(o -> o.fecha != null && o.fecha.atZone(ZoneId.systemDefault()).toLocalDate().equals(today))
                .count();

        Map<String, Long> quantityByProduct = new LinkedHashMap<>();
        for (Order order : orders) {
            for (Order.OrderItem item : order.items) {
                quantityByProduct.merge(item.productId, (long) item.quantity, Long::sum);
            }
        }

        List<TopProductStatDTO> topProducts = quantityByProduct.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Product product = productRepository.findById(entry.getKey()).orElse(null);
                    String name = product != null ? product.name : entry.getKey();
                    return new TopProductStatDTO(entry.getKey(), name, entry.getValue());
                })
                .toList();

        return ResponseEntity.ok(new OrderStatsDTO(totalOrders, totalRevenue, ordersToday, topProducts));
    }

    @AdminOnly
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Orden no encontrada"));
        }

        String nuevoEstado = body.get("estado");
        if (nuevoEstado == null || nuevoEstado.isBlank()) {
            return Problems.of(400, "estado-requerido", "Estado requerido", "Falta el campo 'estado'.");
        }

        // El cambio pasa por la máquina de estados de la venta (patrón State): una transición que el estado actual no
        // permite responde 409, y cancelar libera el stock y reembolsa si ya se cobró.
        VentaService.VentaOutcome outcome = ventaService.cambiarEstado(order, nuevoEstado);
        if (outcome.isProblem()) {
            return Problems.of(outcome.status(), outcome.problemSlug(), outcome.problemTitle(), outcome.problemDetail());
        }
        return ResponseEntity.ok(OrderDTO.forAdmin(outcome.order()));
    }
}
