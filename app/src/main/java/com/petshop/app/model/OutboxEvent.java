package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Evento de una venta que todavía tiene que llegar al broker (patrón "outbox"). Se guarda en la base de datos y un proceso
 * lo envía con reintentos: si el broker está caído, el evento espera acá y no se pierde.
 */
@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_pendientes", columnList = "sentAt, id"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Identificador único del evento: viaja con el mensaje y permite que el consumidor descarte duplicados. */
    @Column(nullable = false, unique = true, length = 36)
    public String eventId;

    @Column(nullable = false)
    public String queue;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Column(nullable = false)
    public Instant createdAt;

    /** Cuándo se entregó al broker; null mientras esté pendiente. */
    public Instant sentAt;

    public int attempts;

    @Column(length = 500)
    public String lastError;

    public OutboxEvent() {}

    public OutboxEvent(String eventId, String queue, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.queue = queue;
        this.payload = payload;
        this.createdAt = createdAt;
    }
}
