package com.petshop.app.controller;

import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.model.Return;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.repository.ReturnRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    private ReturnRepository returnRepository;
    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private com.petshop.app.repository.OrderRepository orderRepository;
    private JwtUtil jwtUtil;
    private AdminGuard adminGuard;
    private ReturnController controller;
    private String adminToken;

    @BeforeEach
    void setUp() {
        returnRepository = mock(ReturnRepository.class);
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        orderRepository = mock(com.petshop.app.repository.OrderRepository.class);
        jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        adminGuard = new AdminGuard(jwtUtil);
        controller = new ReturnController(returnRepository, productRepository, variantRepository, orderRepository, jwtUtil, adminGuard);
        adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");

        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approvingReturnRestocksProductAndVariant() {
        Product product = new Product("p1", "Collar", "Marca", 500.0, null, 4.5, "/img.jpg", null, "accesorios", 3);
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        ProductVariant variant = new ProductVariant("p1", "M", "Negro", 1);
        variant.id = 10L;
        when(variantRepository.findById(10L)).thenReturn(Optional.of(variant));

        Return devolucion = new Return("user-1", "p1", 2, "No le gustó", Return.Status.PENDIENTE, Instant.now());
        devolucion.id = 1L;
        devolucion.variantId = 10L;
        when(returnRepository.findById(1L)).thenReturn(Optional.of(devolucion));

        ResponseEntity<?> response = controller.updateStatus(adminToken, 1L, Map.of("estado", "APROBADA"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(product.stock).isEqualTo(5);
        assertThat(variant.stock).isEqualTo(3);
    }

    @Test
    void rejectingReturnDoesNotRestockAnything() {
        Product product = new Product("p2", "Correa", "Marca", 300.0, null, 4.0, "/img.jpg", null, "accesorios", 5);
        when(productRepository.findById("p2")).thenReturn(Optional.of(product));

        Return devolucion = new Return("user-1", "p2", 2, "Producto dañado", Return.Status.PENDIENTE, Instant.now());
        devolucion.id = 2L;
        when(returnRepository.findById(2L)).thenReturn(Optional.of(devolucion));

        ResponseEntity<?> response = controller.updateStatus(adminToken, 2L, Map.of("estado", "RECHAZADA"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(product.stock).isEqualTo(5);
    }

    @Test
    void cannotResolveAReturnTwice() {
        Product product = new Product("p3", "Cama", "Marca", 1000.0, null, 4.0, "/img.jpg", null, "accesorios", 1);
        when(productRepository.findById("p3")).thenReturn(Optional.of(product));

        Return devolucion = new Return("user-1", "p3", 1, "Motivo", Return.Status.APROBADA, Instant.now());
        devolucion.id = 3L;
        when(returnRepository.findById(3L)).thenReturn(Optional.of(devolucion));

        ResponseEntity<?> response = controller.updateStatus(adminToken, 3L, Map.of("estado", "APROBADA"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(product.stock).isEqualTo(1);
    }

    @Test
    void nonAdminCannotResolveReturns() {
        String customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");

        ResponseEntity<?> response = controller.updateStatus(customerToken, 1L, Map.of("estado", "APROBADA"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    // ---- Pedir una devolución: solo de lo que se compró -----------------------------------------------------------------

    private String customerToken() {
        return jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
    }

    private com.petshop.app.model.Order order(String estado, double price, int quantity) {
        com.petshop.app.model.Order order = new com.petshop.app.model.Order("user-1", Instant.now(),
                java.util.List.of(new com.petshop.app.model.Order.OrderItem("p1", quantity, price)), price * quantity, estado);
        return order;
    }

    private void boughtOrders(com.petshop.app.model.Order... orders) {
        when(productRepository.existsById("p1")).thenReturn(true);
        when(orderRepository.findByUserIdOrderByFechaDesc("user-1")).thenReturn(java.util.List.of(orders));
    }

    private ResponseEntity<?> ask(int quantity) {
        return controller.request(customerToken(), Map.of("productId", "p1", "cantidad", quantity, "motivo", "No me gustó"));
    }

    @Test
    void aCustomerCanReturnWhatTheyBoughtAndTheRequestStartsPending() {
        boughtOrders(order("COMPLETADA", 500, 2));

        ResponseEntity<?> response = ask(2);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        org.mockito.ArgumentCaptor<Return> saved = org.mockito.ArgumentCaptor.forClass(Return.class);
        org.mockito.Mockito.verify(returnRepository).save(saved.capture());
        assertThat(saved.getValue().estado).isEqualTo(Return.Status.PENDIENTE);
        assertThat(saved.getValue().userId).isEqualTo("user-1");
        assertThat(saved.getValue().cantidad).isEqualTo(2);
    }

    @Test
    void aProductTheCustomerNeverBoughtCannotBeReturned() {
        boughtOrders();

        ResponseEntity<?> response = ask(1);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        org.mockito.Mockito.verify(returnRepository, org.mockito.Mockito.never()).save(any(Return.class));
    }

    @Test
    void moreUnitsThanBoughtCannotBeReturned() {
        boughtOrders(order("COMPLETADA", 500, 2));

        ResponseEntity<?> response = ask(3);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(String.valueOf(response.getBody())).contains("2 unidad");
        org.mockito.Mockito.verify(returnRepository, org.mockito.Mockito.never()).save(any(Return.class));
    }

    @Test
    void onlyOrdersThatWentThroughCountAsBought() {
        boughtOrders(order("CANCELADA", 500, 2), order("PAGO_PENDIENTE", 500, 2));

        assertThat(ask(1).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void shippedOrdersCountAsBought() {
        boughtOrders(order("ENVIADA", 500, 1));

        assertThat(ask(1).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void aFreeGiftCannotBeReturnedForMoney() {
        boughtOrders(order("COMPLETADA", 0, 1));

        assertThat(ask(1).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void unitsFromSeveralOrdersAddUp() {
        boughtOrders(order("COMPLETADA", 500, 1), order("ENVIADA", 500, 2));

        assertThat(ask(3).getStatusCode().value()).isEqualTo(200);
        assertThat(ask(4).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void unitsAlreadyRequestedAreDiscountedButRejectedRequestsAreNot() {
        boughtOrders(order("COMPLETADA", 500, 3));
        Return pending = new Return("user-1", "p1", 2, "x", Return.Status.PENDIENTE, Instant.now());
        Return rejected = new Return("user-1", "p1", 1, "x", Return.Status.RECHAZADA, Instant.now());
        Return otherProduct = new Return("user-1", "p9", 3, "x", Return.Status.PENDIENTE, Instant.now());
        when(returnRepository.findByUserIdOrderByRequestedAtDesc("user-1")).thenReturn(java.util.List.of(pending, rejected, otherProduct));

        assertThat(ask(1).getStatusCode().value()).as("queda 1 de las 3").isEqualTo(200);
        assertThat(ask(2).getStatusCode().value()).as("pedir 2 supera lo que queda").isEqualTo(422);
    }

    @Test
    void ordersOfAnotherCustomerDoNotCount() {
        when(productRepository.existsById("p1")).thenReturn(true);
        when(orderRepository.findByUserIdOrderByFechaDesc("user-1")).thenReturn(java.util.List.of());
        when(orderRepository.findByUserIdOrderByFechaDesc("otro")).thenReturn(java.util.List.of(order("COMPLETADA", 500, 5)));

        assertThat(ask(1).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void requestingAReturnNeedsASessionAnExistingProductAndAValidQuantity() {
        assertThat(controller.request(null, Map.of("productId", "p1", "cantidad", 1)).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.request("token-roto", Map.of("productId", "p1", "cantidad", 1)).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.request(customerToken(), Map.of("cantidad", 1)).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.request(customerToken(), Map.of("productId", "no-existe", "cantidad", 1)).getStatusCode().value()).isEqualTo(400);
        boughtOrders(order("COMPLETADA", 500, 2));
        assertThat(ask(0).getStatusCode().value()).isEqualTo(400);
        assertThat(ask(-1).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.request(customerToken(), Map.of("productId", "p1", "cantidad", "dos")).getStatusCode().value()).isEqualTo(400);
    }
}
