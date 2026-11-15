package com.petshop.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String productId;
    public String userId;
    public int rating;
    public String comment;
    public Instant createdAt;

    public Review() {}

    public Review(String productId, String userId, int rating, String comment, Instant createdAt) {
        this.productId = productId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }
}
