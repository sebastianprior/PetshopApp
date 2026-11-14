package com.petshop.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "wishlist_items")
public class WishlistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String userId;
    public String productId;
    public Instant addedAt;

    public WishlistItem() {}

    public WishlistItem(String userId, String productId, Instant addedAt) {
        this.userId = userId;
        this.productId = productId;
        this.addedAt = addedAt;
    }
}
