package com.petshop.app.service;

import com.petshop.app.model.Product;

/** Precio que realmente se cobra por un producto. */
public final class Pricing {

    private Pricing() {}

    /**
     * El precio promocional si el producto tiene uno válido (positivo y menor al precio normal); si no, el precio normal.
     * Es el único lugar que decide el precio: el carrito, el checkout, la ruleta y los cupones trabajan sobre este valor,
     * así que lo que ve el cliente en la tarjeta es lo que paga.
     */
    public static double effectivePrice(Product product) {
        Double promo = product.precioPromocional;
        if (promo != null && promo > 0 && promo < product.price) {
            return promo;
        }
        return product.price;
    }
}
