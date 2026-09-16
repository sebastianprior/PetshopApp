package com.petshop.app.repository;

import com.petshop.app.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByFechaDesc(String userId);
    List<Order> findAllByOrderByFechaDesc();
}
