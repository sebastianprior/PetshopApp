package com.petshop.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String productId;
    public String talle;
    public String color;
    public int stock;
    public String imageUrl;

    public ProductVariant() {}

    public ProductVariant(String productId, String talle, String color, int stock) {
        this.productId = productId;
        this.talle = talle;
        this.color = color;
        this.stock = stock;
    }
}
