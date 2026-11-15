package com.petshop.app.controller;

import com.petshop.app.model.CartItem;
import com.petshop.app.model.Product;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El carrito cobra el precio vigente: el promocional si el producto tiene uno. */
class CartPricingTest {

    private static final String GUEST = "abc";

    private Product ball;
    private InMemoryStore store;
    private CartController controller;

    @BeforeEach
    void setUp() {
        ball = new Product("p2", "Pelota Squeaky", "PetPlay", 499, null, 4.3, "/products/p2.svg", null, "juguetes", 50);
        ball.precioPromocional = 349.0;
        ProductRepository products = mock(ProductRepository.class);
        when(products.findById("p2")).thenReturn(Optional.of(ball));
        store = new InMemoryStore();
        controller = new CartController(store, products, mock(CartItemRepository.class), mock(OrderRepository.class),
                new JwtUtil("test-secret-test-secret-test-secret-test-secret", 60_000), mock(NotificationService.class),
                mock(ProductVariantRepository.class), mock(CouponRepository.class));
    }

    private CartItem requested(double clientPrice) {
        return new CartItem("p2", "Pelota Squeaky", "juguetes", 1, clientPrice);
    }

    @SuppressWarnings("unchecked")
    private List<CartItem> items(ResponseEntity<?> response) {
        return (List<CartItem>) response.getBody();
    }

    @Test
    void addingAProductOnPromotionStoresThePromotionalPriceNoMatterWhatTheClientSends() {
        List<CartItem> cart = items(controller.add(null, GUEST, requested(1.0)));

        assertThat(cart).hasSize(1);
        assertThat(cart.get(0).price).isEqualTo(349.0);
    }

    @Test
    void withoutPromotionTheRegularPriceIsStored() {
        ball.precioPromocional = null;

        assertThat(items(controller.add(null, GUEST, requested(1.0))).get(0).price).isEqualTo(499.0);
    }

    @Test
    void ifThePromotionIsRemovedLaterTheCartShowsTheRegularPrice() {
        controller.add(null, GUEST, requested(349.0));
        ball.precioPromocional = null;

        List<CartItem> cart = items(controller.getCart(null, GUEST));

        assertThat(cart.get(0).price).isEqualTo(499.0);
    }

    @Test
    void ifAPromotionIsAddedLaterTheCartShowsTheNewPriceAndAddingMoreUnitsKeepsItInSync() {
        ball.precioPromocional = null;
        controller.add(null, GUEST, requested(499.0));
        ball.precioPromocional = 299.0;

        assertThat(items(controller.getCart(null, GUEST)).get(0).price).isEqualTo(299.0);

        List<CartItem> afterAdding = items(controller.add(null, GUEST, requested(499.0)));
        assertThat(afterAdding).hasSize(1);
        assertThat(afterAdding.get(0).quantity).isEqualTo(2);
        assertThat(afterAdding.get(0).price).isEqualTo(299.0);
    }

    @Test
    void changingTheQuantityKeepsTheCurrentPrice() {
        controller.add(null, GUEST, requested(349.0));
        ball.precioPromocional = 250.0;

        List<CartItem> cart = items(controller.increment(null, GUEST, "p2", null));

        assertThat(cart.get(0).quantity).isEqualTo(2);
        assertThat(cart.get(0).price).isEqualTo(250.0);
    }
}
