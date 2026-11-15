package com.petshop.app.model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "returns")
public class Return {

    public enum Status { PENDIENTE, APROBADA, RECHAZADA, PROCESADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String userId;
    public String productId;
    public Long variantId;
    public String variant;
    public int cantidad;
    public String motivo;

    @Enumerated(EnumType.STRING)
    public Status estado;

    public Instant requestedAt;

    public Return() {}

    public Return(String userId, String productId, int cantidad, String motivo, Status estado, Instant requestedAt) {
        this.userId = userId;
        this.productId = productId;
        this.cantidad = cantidad;
        this.motivo = motivo;
        this.estado = estado;
        this.requestedAt = requestedAt;
    }
}
