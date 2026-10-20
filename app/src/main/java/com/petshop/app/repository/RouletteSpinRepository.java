package com.petshop.app.repository;

import com.petshop.app.model.RouletteSpin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RouletteSpinRepository extends JpaRepository<RouletteSpin, Long> {
    Optional<RouletteSpin> findByOwnerKeyAndDay(String ownerKey, LocalDate day);
}
