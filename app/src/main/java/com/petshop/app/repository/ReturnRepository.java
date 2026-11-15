package com.petshop.app.repository;

import com.petshop.app.model.Return;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRepository extends JpaRepository<Return, Long> {
    List<Return> findByUserIdOrderByRequestedAtDesc(String userId);
    List<Return> findAllByOrderByRequestedAtDesc();
}
