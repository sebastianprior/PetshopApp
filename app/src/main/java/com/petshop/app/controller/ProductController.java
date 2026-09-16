package com.petshop.app.controller;

import com.petshop.app.model.Product;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.PriceAscStrategy;
import com.petshop.app.service.PriceDescStrategy;
import com.petshop.app.service.ProductSortStrategy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductSortStrategy priceAscStrategy;
    private final ProductSortStrategy priceDescStrategy;
    private final AdminGuard adminGuard;

    public ProductController(ProductRepository productRepository, PriceAscStrategy priceAscStrategy,
                              PriceDescStrategy priceDescStrategy, AdminGuard adminGuard) {
        this.productRepository = productRepository;
        this.priceAscStrategy = priceAscStrategy;
        this.priceDescStrategy = priceDescStrategy;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public List<Product> list(@RequestParam(required = false) String category, @RequestParam(required = false) String sort) {
        List<Product> filtered = (category == null || category.isBlank())
                ? productRepository.findAll()
                : productRepository.findByCategoryIdIgnoreCase(category);

        ProductSortStrategy strategy = resolveStrategy(sort);
        if (strategy != null) {
            filtered = strategy.sort(filtered);
        }

        return filtered;
    }

    private ProductSortStrategy resolveStrategy(String sort) {
        if ("Menor precio".equalsIgnoreCase(sort) || "menorprecio".equalsIgnoreCase(sort)) {
            return priceAscStrategy;
        }
        if ("Mayor precio".equalsIgnoreCase(sort) || "mayorprecio".equalsIgnoreCase(sort)) {
            return priceDescStrategy;
        }
        return null;
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable String id) {
        return productRepository.findById(id).orElse(null);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestBody Product product) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        if (product.id == null || product.id.isBlank()) {
            product.id = UUID.randomUUID().toString();
        }
        productRepository.save(product);
        return ResponseEntity.ok(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @PathVariable String id,
                                     @RequestBody Product product) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        if (!productRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }

        product.id = id;
        productRepository.save(product);
        return ResponseEntity.ok(product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @PathVariable String id) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        if (!productRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }

        productRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
