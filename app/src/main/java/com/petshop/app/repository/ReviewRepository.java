package com.petshop.app.repository;

import com.petshop.app.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdOrderByCreatedAtDesc(String productId);
    boolean existsByProductIdAndUserId(String productId, String userId);
}
