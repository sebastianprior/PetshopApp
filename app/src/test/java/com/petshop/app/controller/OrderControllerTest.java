package com.petshop.app.controller;

import com.petshop.app.model.Order;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** El cambio de estado del panel de administrador pasa por la máquina de estados de la venta (ver EstadoVentaTest). */
class OrderControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    private OrderRepository orderRepository;
    private VentaService ventaService;
    private OrderController controller;
    private String adminToken;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        ventaService = mock(VentaService.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        JwtUtil jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        AdminGuard adminGuard = new AdminGuard(jwtUtil);
        controller = new OrderController(orderRepository, productRepository, jwtUtil, adminGuard, ventaService);
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
    }

    private Order order(String estado) {
        Order order = new Order("user-1", Instant.now(), List.of(), 100.0, estado);
        order.id = 1L;
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void anAllowedTransitionIsDelegatedToTheStateMachineAndReturns200() {
        Order order = order("COMPLETADA");
        when(ventaService.cambiarEstado(order, "ENVIADA")).thenAnswer(inv -> {
            order.estado = "ENVIADA";
            return new VentaOutcome(200, order, null, null, null);
        });

        ResponseEntity<?> response = controller.updateStatus(adminToken, 1L, Map.of("estado", "ENVIADA"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(order.estado).isEqualTo("ENVIADA");
    }

    @Test
    void aTransitionTheCurrentStateDoesNotAllowIsA409WithProblemDetails() {
        Order order = order("CANCELADA");
        when(ventaService.cambiarEstado(order, "ENVIADA")).thenReturn(new VentaOutcome(409, order,
                "transicion-invalida", "Transición inválida", "Una venta en estado CANCELADA no admite la acción 'enviar'."));

        ResponseEntity<?> response = controller.updateStatus(adminToken, 1L, Map.of("estado", "ENVIADA"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getType().toString()).isEqualTo("urn:petshop:problem:transicion-invalida");
        assertThat(problem.getDetail()).contains("CANCELADA");
        assertThat(order.estado).isEqualTo("CANCELADA");
    }

    @Test
    void anUnknownStatusIsRejectedWith422AndTheOrderDoesNotChange() {
        Order order = order("COMPLETADA");
        when(ventaService.cambiarEstado(order, "EN_LA_LUNA")).thenReturn(new VentaOutcome(422, order,
                "estado-invalido", "Estado inválido", "El estado debe ser ENVIADA o CANCELADA."));

        ResponseEntity<?> response = controller.updateStatus(adminToken, 1L, Map.of("estado", "EN_LA_LUNA"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(order.estado).isEqualTo("COMPLETADA");
    }

    @Test
    void aMissingStatusIsA400BeforeAnythingIsTouched() {
        order("COMPLETADA");

        ResponseEntity<?> response = controller.updateStatus(adminToken, 1L, Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(ventaService);
    }

    @Test
    void anUnknownOrderIsRejectedWithoutCallingTheStateMachine() {
        when(orderRepository.findById(7L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.updateStatus(adminToken, 7L, Map.of("estado", "ENVIADA"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        verifyNoInteractions(ventaService);
    }

    @Test
    void cancellingFromTheAdminPanelGoesThroughTheServiceSoStockIsReleased() {
        Order order = order("COMPLETADA");
        when(ventaService.cambiarEstado(order, "CANCELADA")).thenReturn(new VentaOutcome(200, order, null, null, null));

        controller.updateStatus(adminToken, 1L, Map.of("estado", "CANCELADA"));

        verify(ventaService).cambiarEstado(order, "CANCELADA");
    }
}
