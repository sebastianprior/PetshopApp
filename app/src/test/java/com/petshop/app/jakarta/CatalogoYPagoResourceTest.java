package com.petshop.app.jakarta;

import com.petshop.app.controller.ProductController;
import com.petshop.app.model.Order;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogoYPagoResourceTest {

    private OrderRepository orderRepository;
    private ProductController productController;
    private CatalogoYPagoResource resource;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productController = mock(ProductController.class);
        JwtUtil jwtUtil = new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000);
        resource = new CatalogoYPagoResource(productController, orderRepository, jwtUtil, new AdminGuard(jwtUtil));
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");

        Order order = new Order("user-1", Instant.now(), List.of(), 100.0, "COMPLETADA");
        order.id = 5L;
        order.estadoPago = "APROBADO";
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        Order guestOrder = new Order("guest:abc", Instant.now(), List.of(), 50.0, "COMPLETADA");
        guestOrder.id = 6L;
        when(orderRepository.findById(6L)).thenReturn(Optional.of(guestOrder));
        when(orderRepository.findById(9L)).thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(Response response) {
        return (Map<String, Object>) response.getEntity();
    }

    @Test
    void theCatalogDelegatesToTheProductController() {
        when(productController.list(null, null, "collar")).thenReturn(List.of());

        assertThat(resource.productos(null, null, "collar").getStatus()).isEqualTo(200);
    }

    @Test
    void ownerAndAdminSeeThePaymentWhileOthersGetProblemDetails() {
        Response owner = resource.pago(5L, customerToken, null);
        assertThat(owner.getStatus()).isEqualTo(200);
        assertThat(body(owner)).containsEntry("estadoPago", "APROBADO");

        assertThat(resource.pago(5L, adminToken, null).getStatus()).isEqualTo(200);

        Response stranger = resource.pago(6L, customerToken, null);
        assertThat(stranger.getStatus()).isEqualTo(404);
        assertThat(stranger.getMediaType().toString()).isEqualTo("application/problem+json");
        assertThat(body(stranger)).containsEntry("type", "urn:petshop:problem:no-encontrado");
    }

    @Test
    void guestsAreIdentifiedByTheirGuestId() {
        assertThat(resource.pago(6L, null, "abc").getStatus()).isEqualTo(200);
        assertThat(resource.pago(6L, null, "otro").getStatus()).isEqualTo(404);
        assertThat(resource.pago(9L, null, "abc").getStatus()).isEqualTo(404);
    }

    @Test
    void withoutCredentialsItAnswers401() {
        Response response = resource.pago(5L, null, null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body(response)).containsEntry("type", "urn:petshop:problem:no-autorizado");
    }
}
