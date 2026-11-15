package com.petshop.app.repository;

import com.petshop.app.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByReturnId(Long returnId);
}
