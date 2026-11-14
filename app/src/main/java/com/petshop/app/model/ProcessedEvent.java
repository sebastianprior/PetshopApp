package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Evento que el consumidor ya procesó: si el broker lo entrega de nuevo, se descarta (consumidor idempotente). */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(length = 36)
    public String eventId;

    public String queue;

    public Instant processedAt;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId, String queue, Instant processedAt) {
        this.eventId = eventId;
        this.queue = queue;
        this.processedAt = processedAt;
    }
}
