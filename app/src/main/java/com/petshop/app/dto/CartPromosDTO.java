package com.petshop.app.dto;

/** Envío gratis y producto de regalo del carrito: lo que se lee en la tienda y lo que guarda el administrador. */
public class CartPromosDTO {
    public boolean freeShippingEnabled;
    public double freeShippingThreshold;
    public boolean giftEnabled;
    public double giftMinAmount;
    public String giftProductId;

    // Solo lectura: datos del producto de regalo para mostrarlo en el carrito
    public String giftProductName;
    public String giftProductImageUrl;
    /** El producto de regalo existe, no tiene variantes y tiene stock (si no, no se ofrece). */
    public boolean giftAvailable;

    public CartPromosDTO() {}
}
