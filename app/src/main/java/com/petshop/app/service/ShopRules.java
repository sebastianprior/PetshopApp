package com.petshop.app.service;

import com.petshop.app.model.CartItem;
import com.petshop.app.model.Product;
import com.petshop.app.model.SiteSettings;
import com.petshop.app.repository.ProductRepository;
import com.petshop.app.model.ShippingZone;
import com.petshop.app.repository.ProductVariantRepository;
import com.petshop.app.repository.ShippingZoneRepository;
import com.petshop.app.repository.SiteSettingsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas comerciales del carrito que configura el administrador: costo de envío (con envío gratis a partir de un monto)
 * y producto de regalo. Es la única fuente de verdad: el checkout la usa para cobrar y la tienda la muestra al cliente.
 * El monto que se compara es el subtotal del carrito (con el precio vigente, antes de cupón y ruleta).
 */
@Service
public class ShopRules {

    /** Lo que se cobra y lo que se promete si el administrador no configuró otra cosa. */
    public static final double DEFAULT_SHIPPING_COST = 1500.0;
    public static final String DEFAULT_SHIPPING_ETA = "3 a 7 días hábiles";
    public static final String REST_OF_COUNTRY = "Resto del país";
    public static final String GIFT_LABEL = "Regalo";

    /** Código postal argentino: 4 números, con o sin la letra de provincia y las 3 letras de la cuadra (C1425ABC). */
    private static final Pattern POSTAL_CODE = Pattern.compile("^[A-Za-z]?(\\d{4})(?:[A-Za-z]{3})?$");

    /** Costo y plazo del envío a un código postal. */
    public record Quote(int postalCode, String zoneName, double cost, String eta, boolean matchedZone) {}

    /** Configuración vigente; sin nada guardado, todo está desactivado. */
    public record CartPromos(boolean freeShippingEnabled, double freeShippingThreshold,
                             boolean giftEnabled, String giftProductId, double giftMinAmount) {}

    private final SiteSettingsRepository settingsRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ShippingZoneRepository zoneRepository;

    public ShopRules(SiteSettingsRepository settingsRepository, ProductRepository productRepository,
                     ProductVariantRepository variantRepository, ShippingZoneRepository zoneRepository) {
        this.settingsRepository = settingsRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.zoneRepository = zoneRepository;
    }

    /** Los 4 dígitos del código postal que escribió el cliente, o null si no es un código válido. */
    public static Integer parsePostalCode(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = POSTAL_CODE.matcher(raw.trim());
        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** Costo y plazo de enviar a ese código postal: el de su zona o, si no está en ninguna, el del resto del país. */
    public Quote quote(int postalCode) {
        for (ShippingZone zone : zoneRepository.findAllByOrderByPostalFromAsc()) {
            if (postalCode >= zone.postalFrom && postalCode <= zone.postalTo) {
                return new Quote(postalCode, zone.name, zone.cost, zone.eta, true);
            }
        }
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElse(null);
        double cost = settings == null || settings.shippingDefaultCost == null ? DEFAULT_SHIPPING_COST : settings.shippingDefaultCost;
        String eta = settings == null || settings.shippingDefaultEta == null || settings.shippingDefaultEta.isBlank()
                ? DEFAULT_SHIPPING_ETA : settings.shippingDefaultEta;
        return new Quote(postalCode, REST_OF_COUNTRY, cost, eta, false);
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

    /** Costo de envío de un carrito con ese subtotal a ese código postal: gratis si alcanzó el monto, si no el de su zona. */
    public double shippingCost(double subtotal, int postalCode) {
        CartPromos promos = promos();
        if (promos.freeShippingEnabled() && promos.freeShippingThreshold() > 0 && subtotal >= promos.freeShippingThreshold()) {
            return 0;
        }
        return quote(postalCode).cost();
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
