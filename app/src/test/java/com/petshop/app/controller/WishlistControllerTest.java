package com.petshop.app.controller;

import com.petshop.app.model.WishlistItem;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.WishlistItemRepository;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WishlistControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    private WishlistItemRepository wishlistItemRepository;
    private ProductRepository productRepository;
    private WishlistController controller;
    private String token;

    @BeforeEach
    void setUp() {
        wishlistItemRepository = mock(WishlistItemRepository.class);
        productRepository = mock(ProductRepository.class);
        JwtUtil jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        controller = new WishlistController(wishlistItemRepository, productRepository, jwtUtil);
        token = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");
    }

    @Test
    void addingAProductForTheFirstTimeSavesIt() {
        when(productRepository.existsById("p1")).thenReturn(true);
        when(wishlistItemRepository.findByUserIdAndProductId("user-1", "p1")).thenReturn(Optional.empty());
        when(wishlistItemRepository.findByUserIdOrderByAddedAtDesc("user-1"))
                .thenReturn(List.of(new WishlistItem("user-1", "p1", Instant.now())));

        ResponseEntity<?> response = controller.add(token, Map.of("productId", "p1"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> body = (List<String>) response.getBody();
        assertThat(body).containsExactly("p1");
    }

    @Test
    void addingTheSameProductTwiceDoesNotDuplicateIt() {
        WishlistItem existing = new WishlistItem("user-1", "p1", Instant.now());
        when(productRepository.existsById("p1")).thenReturn(true);
        when(wishlistItemRepository.findByUserIdAndProductId("user-1", "p1")).thenReturn(Optional.of(existing));
        when(wishlistItemRepository.findByUserIdOrderByAddedAtDesc("user-1")).thenReturn(List.of(existing));

        controller.add(token, Map.of("productId", "p1"));

        verify(wishlistItemRepository, times(0)).save(any());
    }

    @Test
    void removingAProductDeletesIt() {
        WishlistItem existing = new WishlistItem("user-1", "p1", Instant.now());
        when(wishlistItemRepository.findByUserIdAndProductId("user-1", "p1")).thenReturn(Optional.of(existing));
        when(wishlistItemRepository.findByUserIdOrderByAddedAtDesc("user-1")).thenReturn(List.of());

        ResponseEntity<?> response = controller.remove(token, "p1");

        verify(wishlistItemRepository).delete(existing);
        assertThat((List<?>) response.getBody()).isEmpty();
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        assertThat(controller.list(null).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.add(null, Map.of("productId", "p1")).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.remove(null, "p1").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void rejectsAddingAnUnknownProduct() {
        when(productRepository.existsById("ghost")).thenReturn(false);

        ResponseEntity<?> response = controller.add(token, Map.of("productId", "ghost"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
