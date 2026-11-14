package com.petshop.app.controller;

import com.petshop.app.model.Order;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    private OrderRepository orderRepository;
    private OrderController controller;
    private String adminToken;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        JwtUtil jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        AdminGuard adminGuard = new AdminGuard(jwtUtil);
        controller = new OrderController(orderRepository, productRepository, jwtUtil, adminGuard);
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
    }

    @Test
    void acceptsAKnownStatus() {
        Order order = new Order("user-1", Instant.now(), List.of(), 100.0, "PENDIENTE");
        order.id = 1L;
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        ResponseEntity<?> response = controller.updateStatus(adminToken, 1L, Map.of("estado", "ENVIADA"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(order.estado).isEqualTo("ENVIADA");
    }

    @Test
    void rejectsAnUnknownStatus() {
        Order order = new Order("user-1", Instant.now(), List.of(), 100.0, "PENDIENTE");
        order.id = 1L;
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseEntity<?> response = controller.updateStatus(adminToken, 1L, Map.of("estado", "EN_LA_LUNA"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(order.estado).isEqualTo("PENDIENTE");
    }
}
