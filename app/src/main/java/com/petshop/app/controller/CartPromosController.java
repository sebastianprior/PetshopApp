package com.petshop.app.controller;

import com.petshop.app.dto.CartPromosDTO;
import com.petshop.app.model.Product;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import com.petshop.app.service.AdminGuard;
import com.petshop.app.service.ShopRules;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/** Envío gratis y regalo del carrito: lectura pública (la tienda lo muestra), edición solo para administradores. */
@RestController
@RequestMapping("/api/settings/cart-promos")
public class CartPromosController {

    static final double MAX_AMOUNT = 10_000_000;

    private final SiteSettingsRepository repository;
    private final ProductRepository productRepository;
    private final ShopRules shopRules;
    private final AdminGuard adminGuard;

    public CartPromosController(SiteSettingsRepository repository, ProductRepository productRepository,
                                ShopRules shopRules, AdminGuard adminGuard) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.shopRules = shopRules;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public CartPromosDTO get() {
        return toDto(shopRules.promos());
    }

    @PutMapping
    public ResponseEntity<?> update(@RequestHeader(value = "X-Auth-Token", required = false) String token,
                                    @RequestBody CartPromosDTO body) {
        if (!adminGuard.isAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Requiere permisos de administrador"));
        }

        if (body.freeShippingEnabled && !validAmount(body.freeShippingThreshold)) {
            return error("Indicá el monto mínimo para el envío gratis (mayor a 0 y hasta " + (long) MAX_AMOUNT + ").");
        }

        String giftProductId = body.giftProductId == null ? null : body.giftProductId.trim();
        if (body.giftEnabled) {
            if (!validAmount(body.giftMinAmount)) {
                return error("Indicá el monto mínimo para llevarse el regalo (mayor a 0 y hasta " + (long) MAX_AMOUNT + ").");
            }
            if (giftProductId == null || giftProductId.isEmpty()) {
                return error("Elegí el producto de regalo.");
            }
            Optional<Product> gift = productRepository.findById(giftProductId);
            if (gift.isEmpty()) {
                return error("El producto de regalo no existe.");
            }
            if (!shopRules.isGiftable(gift.get())) {
                return error("El producto de regalo debe tener stock y no tener variantes (talle/color).");
            }
        }

        SiteSettings settings = repository.findById(SiteSettings.SINGLETON_ID).orElseGet(SiteSettings::new);
        settings.freeShippingEnabled = body.freeShippingEnabled;
        settings.freeShippingThreshold = validAmount(body.freeShippingThreshold) ? body.freeShippingThreshold : null;
        settings.giftEnabled = body.giftEnabled;
        settings.giftProductId = giftProductId == null || giftProductId.isEmpty() ? null : giftProductId;
        settings.giftMinAmount = validAmount(body.giftMinAmount) ? body.giftMinAmount : null;
        repository.save(settings);
        return ResponseEntity.ok(toDto(shopRules.promos()));
    }

    private CartPromosDTO toDto(ShopRules.CartPromos promos) {
        CartPromosDTO dto = new CartPromosDTO();
        dto.freeShippingEnabled = promos.freeShippingEnabled();
        dto.freeShippingThreshold = promos.freeShippingThreshold();
        dto.giftEnabled = promos.giftEnabled();
        dto.giftMinAmount = promos.giftMinAmount();
        dto.giftProductId = promos.giftProductId();
        if (promos.giftProductId() != null) {
            productRepository.findById(promos.giftProductId()).ifPresent(product -> {
                dto.giftProductName = product.name;
                dto.giftProductImageUrl = product.imageUrl;
                dto.giftAvailable = shopRules.isGiftable(product);
            });
        }
        return dto;
    }

    private static boolean validAmount(double amount) {
        return amount > 0 && amount <= MAX_AMOUNT && !Double.isNaN(amount);
    }

    private static ResponseEntity<?> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
