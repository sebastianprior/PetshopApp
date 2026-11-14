package com.petshop.app.controller;

import com.petshop.app.model.WishlistItem;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.WishlistItemRepository;
import com.petshop.app.service.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final JwtUtil jwtUtil;

    public WishlistController(WishlistItemRepository wishlistItemRepository, ProductRepository productRepository,
                               JwtUtil jwtUtil) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
        this.jwtUtil = jwtUtil;
    }

    @ApiResponse(responseCode = "200", description = "Los identificadores de los productos favoritos", content = @Content(array = @ArraySchema(schema = @Schema(type = "string"))))
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String userId = jwtUtil.extractUserId(token);
        List<String> productIds = wishlistItemRepository.findByUserIdOrderByAddedAtDesc(userId).stream()
                .map(item -> item.productId)
                .toList();
        return ResponseEntity.ok(productIds);
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                  @RequestBody Map<String, String> body) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String productId = body.get("productId");
        if (productId == null || productId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto inválido"));
        }
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }

        String userId = jwtUtil.extractUserId(token);
        if (wishlistItemRepository.findByUserIdAndProductId(userId, productId).isEmpty()) {
            wishlistItemRepository.save(new WishlistItem(userId, productId, Instant.now()));
        }

        List<String> productIds = wishlistItemRepository.findByUserIdOrderByAddedAtDesc(userId).stream()
                .map(item -> item.productId)
                .toList();
        return ResponseEntity.ok(productIds);
    }

    @ApiResponse(responseCode = "200", description = "Los identificadores de los productos favoritos que quedan", content = @Content(array = @ArraySchema(schema = @Schema(type = "string"))))
    @DeleteMapping("/{productId}")
    public ResponseEntity<?> remove(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @PathVariable String productId) {
        if (token == null || !jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        String userId = jwtUtil.extractUserId(token);
        wishlistItemRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(wishlistItemRepository::delete);

        List<String> productIds = wishlistItemRepository.findByUserIdOrderByAddedAtDesc(userId).stream()
                .map(item -> item.productId)
                .toList();
        return ResponseEntity.ok(productIds);
    }
}
