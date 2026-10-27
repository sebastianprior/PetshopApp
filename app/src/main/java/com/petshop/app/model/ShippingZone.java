package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Zona de envío: un rango de códigos postales con su costo y plazo estimado. Editable desde el panel de administración. */
@Entity
@Table(name = "shipping_zones")
public class ShippingZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 80)
    public String name;

    /** Primer y último código postal (4 dígitos) de la zona, ambos incluidos. */
    public int postalFrom;
    public int postalTo;

    public double cost;

    /** Plazo estimado de entrega, en texto libre ("24 a 48 hs"). */
    @Column(length = 80)
    public String eta;

    public ShippingZone() {}

    public ShippingZone(String name, int postalFrom, int postalTo, double cost, String eta) {
        this.name = name;
        this.postalFrom = postalFrom;
        this.postalTo = postalTo;
        this.cost = cost;
        this.eta = eta;
    }
}
