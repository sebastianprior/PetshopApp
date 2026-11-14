package com.petshop.app.dto;

import java.time.Instant;

public class ReviewDTO {
    public Long id;
    public String productId;
    public String authorName;
    public int rating;
    public String comment;
    public Instant createdAt;

    public ReviewDTO() {}

    public ReviewDTO(Long id, String productId, String authorName, int rating, String comment, Instant createdAt) {
        this.id = id;
        this.productId = productId;
        this.authorName = authorName;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }
}
