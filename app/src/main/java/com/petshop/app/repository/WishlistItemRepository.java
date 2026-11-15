package com.petshop.app.repository;

import com.petshop.app.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByUserIdOrderByAddedAtDesc(String userId);
    Optional<WishlistItem> findByUserIdAndProductId(String userId, String productId);
}
