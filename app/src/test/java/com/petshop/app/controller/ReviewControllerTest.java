package com.petshop.app.controller;

import com.petshop.app.model.Review;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ReviewRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-test-secret";

    private ReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private JwtUtil jwtUtil;
    private ReviewController controller;
    private String customerToken;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        productRepository = mock(ProductRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        jwtUtil = new JwtUtil(JWT_SECRET, 60_000);
        AdminGuard adminGuard = new AdminGuard(jwtUtil);
        controller = new ReviewController(reviewRepository, productRepository, userRepository, jwtUtil, adminGuard);
        customerToken = jwtUtil.generateToken("user-1", "cliente@example.com", "CUSTOMER");

        when(productRepository.existsById("p1")).thenReturn(true);
    }

    @Test
    void rejectsASecondReviewFromTheSameUserForTheSameProduct() {
        when(reviewRepository.existsByProductIdAndUserId("p1", "user-1")).thenReturn(true);

        ResponseEntity<?> response = controller.add(
            "p1", customerToken, Map.of("rating", 5, "comment", "Otra vez"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void allowsAFirstReviewFromANewUser() {
        when(reviewRepository.existsByProductIdAndUserId("p1", "user-1")).thenReturn(false);

        ResponseEntity<?> response = controller.add(
            "p1", customerToken, Map.of("rating", 4, "comment", "Muy bueno"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void adminCanDeleteAReview() {
        String adminToken = jwtUtil.generateToken("admin-1", "admin@example.com", "ADMIN");
        Review review = new Review("p1", "user-1", 1, "Spam", Instant.now());
        review.id = 5L;
        when(reviewRepository.findById(5L)).thenReturn(Optional.of(review));

        ResponseEntity<?> response = controller.delete("p1", 5L, adminToken);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void nonAdminCannotDeleteAReview() {
        ResponseEntity<?> response = controller.delete("p1", 5L, customerToken);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
}
