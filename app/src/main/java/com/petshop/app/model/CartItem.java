package com.petshop.app.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String userId;
    public String productId;
    public String name;
    public String variant;
    public Long variantId;
    public int quantity;
    public double price;

    @JsonCreator
    public CartItem() {}

    public CartItem(String productId, String name, String variant, int quantity, double price) {
        this.productId = productId;
        this.name = name;
        this.variant = variant;
        this.quantity = quantity;
        this.price = price;
    }
}
