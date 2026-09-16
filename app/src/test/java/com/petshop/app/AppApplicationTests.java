package com.petshop.app;

import com.petshop.app.controller.CartController;
import com.petshop.app.model.CartItem;
import com.petshop.app.model.Product;
import com.petshop.app.model.Order;
import com.petshop.app.repository.CartItemRepository;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.service.InMemoryStore;
import com.petshop.app.service.JwtUtil;
import com.petshop.app.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppApplicationTests {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    private InMemoryStore store;
    private ProductRepository productRepository;
    private CartItemRepository cartItemRepository;
    private List<CartItem> persistedCart;
    private OrderRepository orderRepository;
    private List<Order> savedOrders;
    private JwtUtil jwtUtil;
    private NotificationService notificationService;
    private CartController cartController;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        productRepository = mock(ProductRepository.class);
        jwtUtil = new JwtUtil(JWT_SECRET, 60_000);

        persistedCart = new ArrayList<>();
        cartItemRepository = mock(CartItemRepository.class);
        when(cartItemRepository.findByUserId("user-1")).thenAnswer(inv -> new ArrayList<>(persistedCart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
            CartItem saved = inv.getArgument(0);
            if (!persistedCart.contains(saved)) {
                persistedCart.add(saved);
            }
            return saved;
        });
        doAnswer(inv -> {
            List<CartItem> toDelete = inv.getArgument(0);
            persistedCart.removeAll(toDelete);
            return null;
        }).when(cartItemRepository).deleteAll(any());
        doAnswer(inv -> {
            persistedCart.remove(inv.getArgument(0));
            return null;
        }).when(cartItemRepository).delete(any(CartItem.class));

        savedOrders = new ArrayList<>();
        orderRepository = mock(OrderRepository.class);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            savedOrders.add(saved);
            return saved;
        });

        notificationService = mock(NotificationService.class);
        cartController = new CartController(store, productRepository, cartItemRepository, orderRepository, jwtUtil, notificationService);
    }

    @Test
    void cartPersistsToDatabaseForLoggedInUsers() {
        Product product = new Product(
            "p-cart-1",
            "Producto carrito",
            "Marca carrito",
            950.0,
            null,
            4.8,
            "/images/cart-test.jpg",
            "Nuevo",
            "alimentos",
            15
        );
        when(productRepository.findById("p-cart-1")).thenReturn(Optional.of(product));

        String token = jwtUtil.generateToken("user-1", "user1@example.com", "CUSTOMER");

        ResponseEntity<?> added = cartController.add(token, new CartItem("p-cart-1", "Producto carrito", "alimentos", 1, 950.0));
        assertThat(added.getStatusCode().is2xxSuccessful()).isTrue();

        List<CartItem> items = (List<CartItem>) added.getBody();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).productId).isEqualTo("p-cart-1");
        assertThat(items.get(0).quantity).isEqualTo(1);
        assertThat(items.get(0).userId).isEqualTo("user-1");
        assertThat(persistedCart).hasSize(1);
        assertThat(store.carts).doesNotContainKey("user-1");

        ResponseEntity<?> addedAgain = cartController.add(token, new CartItem("p-cart-1", "Producto carrito", "alimentos", 1, 950.0));
        List<CartItem> itemsAfterMerge = (List<CartItem>) addedAgain.getBody();
        assertThat(itemsAfterMerge).hasSize(1);
        assertThat(itemsAfterMerge.get(0).quantity).isEqualTo(2);
        assertThat(persistedCart).hasSize(1);

        ResponseEntity<?> checkout = cartController.checkout(token);
        assertThat(checkout.getStatusCode().is2xxSuccessful()).isTrue();

        Map<?, ?> body = (Map<?, ?>) checkout.getBody();
        assertThat(body.get("ok")).isEqualTo(true);
        assertThat(persistedCart).isEmpty();
        verify(notificationService).notify("user1@example.com", "Tu compra de 1 producto(s) se realizó con éxito.");

        assertThat(savedOrders).hasSize(1);
        Order order = savedOrders.get(0);
        assertThat(order.userId).isEqualTo("user-1");
        assertThat(order.estado).isEqualTo("COMPLETADA");
        assertThat(order.total).isEqualTo(1900.0);
        assertThat(order.items).hasSize(1);
        assertThat(order.items.get(0).productId).isEqualTo("p-cart-1");
        assertThat(order.items.get(0).quantity).isEqualTo(2);
        assertThat(order.items.get(0).price).isEqualTo(950.0);
    }

    @Test
    void incrementAndDecrementAdjustCartItemQuantityAndRemoveAtZero() {
        Product product = new Product(
            "p-cart-3",
            "Producto stepper",
            "Marca carrito",
            300.0,
            null,
            4.0,
            "/images/cart-test-3.jpg",
            "Nuevo",
            "alimentos",
            20
        );
        when(productRepository.findById("p-cart-3")).thenReturn(Optional.of(product));

        String token = jwtUtil.generateToken("user-1", "user1@example.com", "CUSTOMER");
        cartController.add(token, new CartItem("p-cart-3", "Producto stepper", "alimentos", 1, 300.0));

        ResponseEntity<?> incremented = cartController.increment(token, "p-cart-3");
        List<CartItem> afterIncrement = (List<CartItem>) incremented.getBody();
        assertThat(afterIncrement).hasSize(1);
        assertThat(afterIncrement.get(0).quantity).isEqualTo(2);

        ResponseEntity<?> decremented = cartController.decrement(token, "p-cart-3");
        List<CartItem> afterDecrement = (List<CartItem>) decremented.getBody();
        assertThat(afterDecrement).hasSize(1);
        assertThat(afterDecrement.get(0).quantity).isEqualTo(1);

        ResponseEntity<?> decrementedAgain = cartController.decrement(token, "p-cart-3");
        List<CartItem> afterSecondDecrement = (List<CartItem>) decrementedAgain.getBody();
        assertThat(afterSecondDecrement).isEmpty();
        assertThat(persistedCart).isEmpty();
    }

    @Test
    void cartFallsBackToGuestBucketForMissingOrInvalidToken() {
        Product product = new Product(
            "p-cart-2",
            "Producto invitado",
            "Marca carrito",
            500.0,
            null,
            4.2,
            "/images/cart-test-2.jpg",
            "Nuevo",
            "alimentos",
            10
        );
        when(productRepository.findById("p-cart-2")).thenReturn(Optional.of(product));

        cartController.add(null, new CartItem("p-cart-2", "Producto invitado", "alimentos", 1, 500.0));
        cartController.add("not-a-real-jwt", new CartItem("p-cart-2", "Producto invitado", "alimentos", 1, 500.0));

        assertThat(store.carts).containsKey("guest");
        assertThat(store.carts.get("guest")).hasSize(1);
        assertThat(store.carts.get("guest").get(0).quantity).isEqualTo(2);
        assertThat(persistedCart).isEmpty();

        cartController.checkout(null);
        verifyNoInteractions(notificationService);
        verifyNoInteractions(orderRepository);
    }
}
