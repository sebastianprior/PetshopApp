package com.petshop.app;

import com.petshop.app.controller.CartController;
import com.petshop.app.model.CartItem;
import com.petshop.app.model.Coupon;
import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.model.Order;
import com.petshop.app.repository.CartItemRepository;
import com.petshop.app.repository.CouponRepository;
import com.petshop.app.repository.OrderRepository;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
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
    private ProductVariantRepository variantRepository;
    private CouponRepository couponRepository;
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
        variantRepository = mock(ProductVariantRepository.class);
        couponRepository = mock(CouponRepository.class);
        cartController = new CartController(store, productRepository, cartItemRepository, orderRepository, jwtUtil, notificationService, variantRepository, couponRepository);
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

        ResponseEntity<?> added = cartController.add(token, null, new CartItem("p-cart-1", "Producto carrito", "alimentos", 1, 950.0));
        assertThat(added.getStatusCode().is2xxSuccessful()).isTrue();

        List<CartItem> items = (List<CartItem>) added.getBody();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).productId).isEqualTo("p-cart-1");
        assertThat(items.get(0).quantity).isEqualTo(1);
        assertThat(items.get(0).userId).isEqualTo("user-1");
        assertThat(persistedCart).hasSize(1);
        assertThat(store.carts).doesNotContainKey("user-1");

        ResponseEntity<?> addedAgain = cartController.add(token, null, new CartItem("p-cart-1", "Producto carrito", "alimentos", 1, 950.0));
        List<CartItem> itemsAfterMerge = (List<CartItem>) addedAgain.getBody();
        assertThat(itemsAfterMerge).hasSize(1);
        assertThat(itemsAfterMerge.get(0).quantity).isEqualTo(2);
        assertThat(persistedCart).hasSize(1);

        assertThat(persistedCart).hasSize(1);
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
        cartController.add(token, null, new CartItem("p-cart-3", "Producto stepper", "alimentos", 1, 300.0));

        ResponseEntity<?> incremented = cartController.increment(token, null, "p-cart-3", null);
        List<CartItem> afterIncrement = (List<CartItem>) incremented.getBody();
        assertThat(afterIncrement).hasSize(1);
        assertThat(afterIncrement.get(0).quantity).isEqualTo(2);

        ResponseEntity<?> decremented = cartController.decrement(token, null, "p-cart-3", null);
        List<CartItem> afterDecrement = (List<CartItem>) decremented.getBody();
        assertThat(afterDecrement).hasSize(1);
        assertThat(afterDecrement.get(0).quantity).isEqualTo(1);

        ResponseEntity<?> decrementedAgain = cartController.decrement(token, null, "p-cart-3", null);
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

        cartController.add(null, "device-1", new CartItem("p-cart-2", "Producto invitado", "alimentos", 1, 500.0));
        cartController.add("not-a-real-jwt", "device-1", new CartItem("p-cart-2", "Producto invitado", "alimentos", 1, 500.0));

        assertThat(store.carts).containsKey("guest:device-1");
        assertThat(store.carts.get("guest:device-1")).hasSize(1);
        assertThat(store.carts.get("guest:device-1").get(0).quantity).isEqualTo(2);
        assertThat(persistedCart).isEmpty();
    }

    @Test
    void differentGuestDevicesGetIsolatedCarts() {
        Product product = new Product(
            "p-cart-4",
            "Producto aislado",
            "Marca carrito",
            200.0,
            null,
            4.1,
            "/images/cart-test-4.jpg",
            "Nuevo",
            "alimentos",
            50
        );
        when(productRepository.findById("p-cart-4")).thenReturn(Optional.of(product));

        cartController.add(null, "device-A", new CartItem("p-cart-4", "Producto aislado", "alimentos", 1, 200.0));
        cartController.add(null, "device-B", new CartItem("p-cart-4", "Producto aislado", "alimentos", 3, 200.0));

        ResponseEntity<?> cartA = cartController.getCart(null, "device-A");
        ResponseEntity<?> cartB = cartController.getCart(null, "device-B");

        assertThat(((List<CartItem>) cartA.getBody()).get(0).quantity).isEqualTo(1);
        assertThat(((List<CartItem>) cartB.getBody()).get(0).quantity).isEqualTo(3);
    }

    @Test
    void addRejectsQuantityAboveAvailableStock() {
        Product product = new Product(
            "p-cart-5",
            "Producto limitado",
            "Marca carrito",
            100.0,
            null,
            4.0,
            "/images/cart-test-5.jpg",
            "Nuevo",
            "alimentos",
            3
        );
        when(productRepository.findById("p-cart-5")).thenReturn(Optional.of(product));

        String token = jwtUtil.generateToken("user-1", "user1@example.com", "CUSTOMER");
        ResponseEntity<?> response = cartController.add(token, null, new CartItem("p-cart-5", "Producto limitado", "alimentos", 5, 100.0));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(persistedCart).isEmpty();
    }

    @Test
    void variantsTrackStockIndependentlyFromEachOtherAndFromProductStock() {
        Product product = new Product(
            "p-variant-1",
            "Remera para perro",
            "Marca variantes",
            800.0,
            null,
            4.5,
            "/images/variant-test.jpg",
            "Nuevo",
            "accesorios",
            100
        );
        when(productRepository.findById("p-variant-1")).thenReturn(Optional.of(product));

        ProductVariant talleM = new ProductVariant("p-variant-1", "M", "Negro", 2);
        talleM.id = 201L;
        ProductVariant talleL = new ProductVariant("p-variant-1", "L", "Negro", 5);
        talleL.id = 202L;
        when(variantRepository.findById(201L)).thenReturn(Optional.of(talleM));
        when(variantRepository.findById(202L)).thenReturn(Optional.of(talleL));

        String token = jwtUtil.generateToken("user-1", "user1@example.com", "CUSTOMER");

        CartItem itemM = new CartItem("p-variant-1", "Remera para perro", null, 2, 800.0);
        itemM.variantId = 201L;
        ResponseEntity<?> addedM = cartController.add(token, null, itemM);
        assertThat(addedM.getStatusCode().is2xxSuccessful()).isTrue();

        CartItem itemMExtra = new CartItem("p-variant-1", "Remera para perro", null, 1, 800.0);
        itemMExtra.variantId = 201L;
        ResponseEntity<?> rejectedM = cartController.add(token, null, itemMExtra);
        assertThat(rejectedM.getStatusCode().is4xxClientError()).isTrue();

        CartItem itemL = new CartItem("p-variant-1", "Remera para perro", null, 5, 800.0);
        itemL.variantId = 202L;
        ResponseEntity<?> addedL = cartController.add(token, null, itemL);
        assertThat(addedL.getStatusCode().is2xxSuccessful()).isTrue();

        List<CartItem> cart = (List<CartItem>) addedL.getBody();
        assertThat(cart).hasSize(2);
        assertThat(cart.get(0).variant).isEqualTo("Talle M / Negro");
        assertThat(cart.get(1).variant).isEqualTo("Talle L / Negro");
    }


    @Test
    void adminsCannotUseTheCart() {
        Product product = new Product(
            "p-admin-cart",
            "Producto",
            "Marca",
            100.0,
            null,
            4.0,
            "/images/admin-cart.jpg",
            "Nuevo",
            "alimentos",
            10
        );
        when(productRepository.findById("p-admin-cart")).thenReturn(Optional.of(product));

        String adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        CartItem item = new CartItem("p-admin-cart", "Producto", "alimentos", 1, 100.0);

        assertThat(cartController.add(adminToken, null, item).getStatusCode().value()).isEqualTo(403);
        assertThat(cartController.increment(adminToken, null, "p-admin-cart", null).getStatusCode().value()).isEqualTo(403);
        assertThat(cartController.decrement(adminToken, null, "p-admin-cart", null).getStatusCode().value()).isEqualTo(403);
        assertThat(cartController.remove(adminToken, null, item).getStatusCode().value()).isEqualTo(403);
        assertThat(persistedCart).isEmpty();
    }


}
