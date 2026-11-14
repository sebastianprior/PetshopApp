package com.petshop.app.dto;

/** Envío gratis y producto de regalo del carrito: lo que se lee en la tienda y lo que guarda el administrador. */
public class CartPromosDTO {
    public boolean freeShippingEnabled;
    public double freeShippingThreshold;
    public boolean giftEnabled;
    public double giftMinAmount;
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
    public String giftProductId;

    // Solo lectura: datos del producto de regalo para mostrarlo en el carrito
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
    public String giftProductName;
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
    public String giftProductImageUrl;
    /** El producto de regalo existe, no tiene variantes y tiene stock (si no, no se ofrece). */
    public boolean giftAvailable;

    public CartPromosDTO() {}
}
