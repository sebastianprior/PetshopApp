package com.petshop.app.repository;

import com.petshop.app.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Los pendientes, del más viejo al más nuevo, para que se entreguen en el orden en que ocurrieron. */
    List<OutboxEvent> findTop50BySentAtIsNullOrderByIdAsc();

    long countBySentAtIsNull();
}
