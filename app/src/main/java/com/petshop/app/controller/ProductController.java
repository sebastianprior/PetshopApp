package com.petshop.app.controller;

import com.petshop.app.model.Product;
import com.petshop.app.model.ProductVariant;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.PriceAscStrategy;
import com.petshop.app.service.PriceDescStrategy;
import com.petshop.app.service.ProductSortStrategy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.petshop.app.config.AdminOnly;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductSortStrategy priceAscStrategy;
    private final ProductSortStrategy priceDescStrategy;
    private final AdminGuard adminGuard;

    public ProductController(ProductRepository productRepository, ProductVariantRepository productVariantRepository,
                              PriceAscStrategy priceAscStrategy, PriceDescStrategy priceDescStrategy, AdminGuard adminGuard) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.priceAscStrategy = priceAscStrategy;
        this.priceDescStrategy = priceDescStrategy;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public List<Product> list(@RequestParam(required = false) String category,
                               @RequestParam(required = false) String sort,
                               @RequestParam(required = false) String search) {
        List<Product> filtered = (category == null || category.isBlank())
                ? productRepository.findAll()
                : productRepository.findByCategoryIdIgnoreCase(category);

        if (search != null && !search.isBlank()) {
            String needle = search.trim().toLowerCase();
            filtered = filtered.stream()
                    .filter(p -> (p.name != null && p.name.toLowerCase().contains(needle))
                            || (p.brand != null && p.brand.toLowerCase().contains(needle)))
                    .collect(Collectors.toList());
        }

        ProductSortStrategy strategy = resolveStrategy(sort);
        if (strategy != null) {
            filtered = strategy.sort(filtered);
        }

        attachHasVariants(filtered);
        return filtered;
    }

    private void attachHasVariants(List<Product> products) {
        List<String> ids = products.stream().map(p -> p.id).toList();
        if (ids.isEmpty()) {
            return;
        }
        Set<String> withVariants = productVariantRepository.findByProductIdIn(ids).stream()
                .map(v -> v.productId)
                .collect(Collectors.toSet());
        for (Product p : products) {
            p.hasVariants = withVariants.contains(p.id);
        }
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
    public ResponseEntity<?> get(@PathVariable String id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Producto no encontrado"));
        }
        product.hasVariants = !productVariantRepository.findByProductId(id).isEmpty();
        return ResponseEntity.ok().eTag(etag(product)).body(product);
    }

    /** Huella de los datos de un producto que el administrador puede editar. */
    private static String etag(Product product) {
        return ETags.of(product.id, product.name, product.brand, product.price, product.oldPrice, product.rating,
                product.imageUrl, product.badge, product.categoryId, product.stock);
    }

    @AdminOnly
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestBody Product product) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        if (product.id == null || product.id.isBlank()) {
            product.id = UUID.randomUUID().toString();
        }
        productRepository.save(product);
        return ResponseEntity.ok(product);
    }

    @AdminOnly
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                     @PathVariable String id,
                                     @RequestBody Product product) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        Product current = productRepository.findById(id).orElse(null);
        if (current == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }
        // Si otra persona lo modificó después de que este administrador lo leyó, no se pisa su cambio (412)
        if (ETags.stale(ifMatch, etag(current))) {
            return Problems.of(412, "precondicion-fallida", "Precondición fallida",
                    "El producto cambió desde que lo leíste (ETag desactualizado): volvé a pedirlo y reintentá.");
        }

        product.id = id;
        productRepository.save(product);
        return ResponseEntity.ok().eTag(etag(product)).body(product);
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                     @PathVariable String id) {
        if (!adminGuard.isAdmin(token)) {
            return adminGuard.denied(token);
        }

        if (!productRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado"));
        }

        productVariantRepository.deleteAll(productVariantRepository.findByProductId(id));
        productRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
