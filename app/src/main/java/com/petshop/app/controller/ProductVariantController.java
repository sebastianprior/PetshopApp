package com.petshop.app.controller;

import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.service.AdminGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.petshop.app.config.AdminOnly;

@RestController
@RequestMapping("/api/products/{productId}/variants")
public class ProductVariantController {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final AdminGuard adminGuard;

    public ProductVariantController(ProductVariantRepository variantRepository, ProductRepository productRepository,
                                     AdminGuard adminGuard) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public List<ProductVariant> list(@PathVariable String productId) {
        return variantRepository.findByProductId(productId);
    }

    @AdminOnly
    @PostMapping
    public ResponseEntity<?> create(@PathVariable String productId,
                                     @RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestBody ProductVariant variant) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }

        ResponseEntity<?> validationError = validate(variant);
        if (validationError != null) {
            return validationError;
        }

        variant.id = null;
        variant.productId = productId;
        variantRepository.save(variant);
        recomputeProductStock(productId);
        return ResponseEntity.ok(variant);
    }

    @AdminOnly
    @PutMapping("/{variantId}")
    public ResponseEntity<?> update(@PathVariable String productId,
                                     @PathVariable Long variantId,
                                     @RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestBody ProductVariant variant) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        ProductVariant existing = variantRepository.findById(variantId).orElse(null);
        if (existing == null || !existing.productId.equals(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Variante no encontrada"));
        }

        ResponseEntity<?> validationError = validate(variant);
        if (validationError != null) {
            return validationError;
        }

        existing.talle = variant.talle;
        existing.color = variant.color;
        existing.stock = variant.stock;
        existing.imageUrl = variant.imageUrl;
        variantRepository.save(existing);
        recomputeProductStock(productId);
        return ResponseEntity.ok(existing);
    }

    @AdminOnly
    @DeleteMapping("/{variantId}")
    public ResponseEntity<?> delete(@PathVariable String productId,
                                     @PathVariable Long variantId,
                                     @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        ProductVariant existing = variantRepository.findById(variantId).orElse(null);
        if (existing == null || !existing.productId.equals(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Variante no encontrada"));
        }

        variantRepository.delete(existing);
        recomputeProductStock(productId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private ResponseEntity<?> validate(ProductVariant variant) {
        boolean hasTalle = variant.talle != null && !variant.talle.isBlank();
        boolean hasColor = variant.color != null && !variant.color.isBlank();
        if (!hasTalle && !hasColor) {
            return ResponseEntity.badRequest().body(Map.of("error", "Indicá al menos talle o color"));
        }
        if (variant.stock < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "El stock no puede ser negativo"));
        }
        return null;
    }

    private void recomputeProductStock(String productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return;
        }
        int total = variantRepository.findByProductId(productId).stream().mapToInt(v -> v.stock).sum();
        product.stock = total;
        productRepository.save(product);
    }
}
