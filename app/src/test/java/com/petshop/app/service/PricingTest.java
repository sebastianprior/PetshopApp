package com.petshop.app.service;

import com.petshop.app.model.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingTest {

    private static Product product(double price, Double promo) {
        Product product = new Product("p", "Pelota", "PetPlay", price, null, 4.0, null, null, "juguetes", 10);
        product.precioPromocional = promo;
        return product;
    }

    @Test
    void aValidPromotionalPriceIsTheOneCharged() {
        assertThat(Pricing.effectivePrice(product(499, 349.0))).isEqualTo(349.0);
    }

    @Test
    void withoutPromotionTheRegularPriceIsCharged() {
        assertThat(Pricing.effectivePrice(product(499, null))).isEqualTo(499.0);
    }

    @Test
    void anInvalidPromotionIsIgnored() {
        assertThat(Pricing.effectivePrice(product(499, 0.0))).isEqualTo(499.0);
        assertThat(Pricing.effectivePrice(product(499, -10.0))).isEqualTo(499.0);
        assertThat(Pricing.effectivePrice(product(499, 499.0))).isEqualTo(499.0);
        assertThat(Pricing.effectivePrice(product(499, 800.0))).isEqualTo(499.0); // una "promo" más cara no es promo
    }
}
