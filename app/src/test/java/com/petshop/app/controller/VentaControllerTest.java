package com.petshop.app.controller;

import com.petshop.app.dto.VentaDTO;
import com.petshop.app.dto.VentaRequest;
import com.petshop.app.model.Order;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.VentaService;
import com.petshop.app.service.VentaService.VentaOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VentaControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    private VentaService ventaService;
    private OrderRepository orderRepository;
    private VentaController controller;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        ventaService = mock(VentaService.class);
        orderRepository = mock(OrderRepository.class);
        JwtUtil jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        controller = new VentaController(ventaService, orderRepository, jwtUtil, new AdminGuard(jwtUtil));
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
    }

    private Order order(Long id, String userId, String estado) {
        Order order = new Order(userId, Instant.now(), List.of(), 100.0, estado);
        order.id = id;
        return order;
    }

    private void assertProblem(ResponseEntity<?> response, int status, String slug) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body.getStatus()).isEqualTo(status);
        assertThat(body.getType().toString()).endsWith(slug);
        assertThat(body.getTitle()).isNotBlank();
        assertThat(body.getDetail()).isNotBlank();
    }

    @Test
    void requiresAValidToken() {
        assertProblem(controller.crear(null, "key", new VentaRequest()), 401, "no-autorizado");
        assertProblem(controller.listar("basura"), 401, "no-autorizado");
    }

    @Test
    void adminsCannotCreateSales() {
        assertProblem(controller.crear(adminToken, "key", new VentaRequest()), 403, "prohibido");
    }

    @Test
    void createRequiresTheIdempotencyKeyHeader() {
        assertProblem(controller.crear(customerToken, null, new VentaRequest()), 400, "idempotency-key-requerida");
        assertProblem(controller.crear(customerToken, "  ", new VentaRequest()), 400, "idempotency-key-requerida");
    }

    @Test
    void approvedSaleReturns201WithLocation() {
        Order order = order(7L, "user-1", "COMPLETADA");
        when(ventaService.crear(anyString(), anyString(), any())).thenReturn(new VentaOutcome(201, order, null, null, null));
        when(ventaService.toDto(order)).thenReturn(new VentaDTO());

        ResponseEntity<?> response = controller.crear(customerToken, "key", new VentaRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/v1/ventas/7");
    }

    @Test
    void pendingSaleReturns202() {
        Order order = order(8L, "user-1", "PAGO_PENDIENTE");
        when(ventaService.crear(anyString(), anyString(), any())).thenReturn(new VentaOutcome(202, order, null, null, null));
        when(ventaService.toDto(order)).thenReturn(new VentaDTO());

        ResponseEntity<?> response = controller.crear(customerToken, "key", new VentaRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/v1/ventas/8");
    }

    @Test
    void rejectedPaymentIsReturnedAsProblemDetails() {
        when(ventaService.crear(anyString(), anyString(), any()))
                .thenReturn(new VentaOutcome(402, null, "pago-rechazado", "Pago rechazado", "Fondos insuficientes"));

        assertProblem(controller.crear(customerToken, "key", new VentaRequest()), 402, "pago-rechazado");
    }

    @Test
    void aCustomerCannotSeeSomeoneElsesSale() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, "otro-usuario", "COMPLETADA")));

        assertProblem(controller.obtener(customerToken, 5L), 404, "no-encontrado");
        assertProblem(controller.pago(customerToken, 5L), 404, "no-encontrado");
        assertProblem(controller.cancelar(customerToken, 5L), 404, "no-encontrado");
    }

    @Test
    void anAdminCanSeeAnySale() {
        Order order = order(5L, "otro-usuario", "COMPLETADA");
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(ventaService.toDto(order)).thenReturn(new VentaDTO());

        assertThat(controller.obtener(adminToken, 5L).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void onlyAdminsCanPatchTheState() {
        assertProblem(controller.cambiarEstado(customerToken, 1L, Map.of("estado", "ENVIADA")), 403, "prohibido");
    }
}
