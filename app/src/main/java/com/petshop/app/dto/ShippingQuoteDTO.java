package com.petshop.app.dto;

/** Cuánto cuesta y cuánto tarda el envío a un código postal (sin considerar el envío gratis, que depende del carrito). */
public class ShippingQuoteDTO {
    public int postalCode;
    /** Nombre de la zona, o "Resto del país" si el código no está en ninguna. */
    public String zoneName;
    public double cost;
    public String eta;
    public boolean matchedZone;

    public ShippingQuoteDTO() {}

    public ShippingQuoteDTO(int postalCode, String zoneName, double cost, String eta, boolean matchedZone) {
        this.postalCode = postalCode;
        this.zoneName = zoneName;
        this.cost = cost;
        this.eta = eta;
        this.matchedZone = matchedZone;
    }
}
