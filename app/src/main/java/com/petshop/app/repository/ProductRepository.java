package com.petshop.app.repository;

import com.petshop.app.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findByCategoryIdIgnoreCase(String categoryId);

    /**
     * Descuenta stock de forma atómica: una sola sentencia que solo actúa si alcanza. Devuelve 1 si se reservó y 0 si no había
     * stock suficiente. Leer el stock, compararlo y reescribirlo en pasos separados permitía vender de más cuando dos personas
     * compraban a la vez.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :cantidad where p.id = :id and p.stock >= :cantidad")
    int reservar(@Param("id") String id, @Param("cantidad") int cantidad);

    /** Devuelve stock (cancelación o devolución aprobada), también de forma atómica. Devuelve 1 si el producto existe. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :cantidad where p.id = :id")
    int liberar(@Param("id") String id, @Param("cantidad") int cantidad);
}
