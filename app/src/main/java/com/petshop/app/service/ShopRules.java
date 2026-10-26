package com.petshop.app.service;

import com.petshop.app.model.CartItem;
import com.petshop.app.model.Product;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Reglas comerciales del carrito que configura el administrador: costo de envío (con envío gratis a partir de un monto)
 * y producto de regalo. Es la única fuente de verdad: el checkout la usa para cobrar y la tienda la muestra al cliente.
 * El monto que se compara es el subtotal del carrito (con el precio vigente, antes de cupón y ruleta).
 */
@Service
public class ShopRules {

    public static final double SHIPPING_COST = 1500.0;
    public static final String GIFT_LABEL = "Regalo";

    /** Configuración vigente; sin nada guardado, todo está desactivado. */
    public record CartPromos(boolean freeShippingEnabled, double freeShippingThreshold,
                             boolean giftEnabled, String giftProductId, double giftMinAmount) {}

    private final SiteSettingsRepository settingsRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public ShopRules(SiteSettingsRepository settingsRepository, ProductRepository productRepository,
                     ProductVariantRepository variantRepository) {
        this.settingsRepository = settingsRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    public CartPromos promos() {
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElse(null);
        if (settings == null) {
            return new CartPromos(false, 0, false, null, 0);
        }
        return new CartPromos(
                Boolean.TRUE.equals(settings.freeShippingEnabled),
                settings.freeShippingThreshold == null ? 0 : settings.freeShippingThreshold,
                Boolean.TRUE.equals(settings.giftEnabled),
                settings.giftProductId,
                settings.giftMinAmount == null ? 0 : settings.giftMinAmount);
    }

    /** Costo de envío de un carrito con ese subtotal (el llamador decide si el carrito está vacío). */
    public double shippingCost(double subtotal) {
        CartPromos promos = promos();
        if (promos.freeShippingEnabled() && promos.freeShippingThreshold() > 0 && subtotal >= promos.freeShippingThreshold()) {
            return 0;
        }
        return SHIPPING_COST;
    }

    /**
     * Producto que se regala con este carrito, si corresponde: función activa, monto alcanzado, el producto existe,
     * no tiene variantes y queda stock para una unidad además de lo que el cliente ya lleva del mismo producto.
     */
    public Optional<Product> giftFor(double subtotal, List<CartItem> cart) {
        CartPromos promos = promos();
        if (!promos.giftEnabled() || promos.giftMinAmount() <= 0 || subtotal < promos.giftMinAmount()
                || promos.giftProductId() == null || promos.giftProductId().isBlank()) {
            return Optional.empty();
        }
        return productRepository.findById(promos.giftProductId()).filter(product -> isGiftable(product, cart));
    }

    /** ¿Se puede regalar este producto? (sin variantes y con al menos una unidad en stock). */
    public boolean isGiftable(Product product) {
        return isGiftable(product, List.of());
    }

    private boolean isGiftable(Product product, List<CartItem> cart) {
        int alreadyInCart = cart.stream().filter(item -> product.id.equals(item.productId)).mapToInt(item -> item.quantity).sum();
        return product.stock >= alreadyInCart + 1 && variantRepository.findByProductId(product.id).isEmpty();
    }
}
