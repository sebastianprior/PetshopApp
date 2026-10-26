package com.petshop.app.repository;

import com.petshop.app.model.FaqItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqItemRepository extends JpaRepository<FaqItem, Long> {
    List<FaqItem> findAllByOrderByPositionAscIdAsc();
}
