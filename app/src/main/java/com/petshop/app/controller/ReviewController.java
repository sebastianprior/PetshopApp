package com.petshop.app.controller;

import com.petshop.app.dto.ReviewDTO;
import com.petshop.app.model.Review;
import com.petshop.app.model.User;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ReviewRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.petshop.app.config.AdminOnly;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AdminGuard adminGuard;

    public ReviewController(ReviewRepository reviewRepository, ProductRepository productRepository,
                             UserRepository userRepository, JwtUtil jwtUtil, AdminGuard adminGuard) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String productId) {
        List<ReviewDTO> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(reviews);
    }

    @PostMapping
    public ResponseEntity<?> add(@PathVariable String productId,
                                  @RequestHeader(value = "X-Auth-Token", required = false) String token,
                                  @RequestBody Map<String, Object> body) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        if (!productRepository.existsById(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }

        Object ratingValue = body.get("rating");
        if (!(ratingValue instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rating inválido"));
        }
        int rating = ((Number) ratingValue).intValue();
        if (rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "El rating debe ser entre 1 y 5"));
        }

        String comment = body.get("comment") != null ? body.get("comment").toString() : "";
        String userId = jwtUtil.extractUserId(token);

        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya dejaste una reseña para este producto"));
        }

        Review review = new Review(productId, userId, rating, comment, Instant.now());
        reviewRepository.save(review);
        return ResponseEntity.ok(toDto(review));
    }

    @AdminOnly
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<?> delete(@PathVariable String productId,
                                     @PathVariable Long reviewId,
                                     @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        Review review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null || !review.productId.equals(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reseña no encontrada"));
        }

        reviewRepository.delete(review);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private ReviewDTO toDto(Review review) {
        User author = userRepository.findById(review.userId).orElse(null);
        String authorName = author != null && author.name != null && !author.name.isBlank() ? author.name : "Usuario";
        return new ReviewDTO(review.id, review.productId, authorName, review.rating, review.comment, review.createdAt);
    }
}
