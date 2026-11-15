package com.petshop.app.repository;

import com.petshop.app.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductId(String productId);
    List<ProductVariant> findByProductIdIn(List<String> productIds);

    /** Descuenta stock de la variante de forma atómica (1 si se reservó, 0 si no alcanzaba). Ver ProductRepository.reservar. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductVariant v set v.stock = v.stock - :cantidad where v.id = :id and v.stock >= :cantidad")
    int reservar(@Param("id") Long id, @Param("cantidad") int cantidad);

    /** Devuelve stock a la variante de forma atómica. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductVariant v set v.stock = v.stock + :cantidad where v.id = :id")
    int liberar(@Param("id") Long id, @Param("cantidad") int cantidad);
}
