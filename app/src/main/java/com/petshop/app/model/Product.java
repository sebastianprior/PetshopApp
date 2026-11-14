package com.petshop.app.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "products")
public class Product {
    @Id
    public String id;
    public String name;
    public String brand;
    public double price;
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
    public Double oldPrice;
    public double rating;
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
    public String imageUrl;
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
    public String badge;
    public String categoryId;
    public int stock;
    public Double precioPromocional;
    public String tipoPromocion;

    @Transient
    public boolean hasVariants;

    @JsonCreator
    public Product() {}

    public Product(String id, String name, String brand, double price, Double oldPrice, double rating, String imageUrl, String badge, String categoryId, int stock) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.price = price;
        this.oldPrice = oldPrice;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.badge = badge;
        this.categoryId = categoryId;
        this.stock = stock;
    }
}
