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
@Table(name = "refunds")
public class Refund {

    public enum Status { PENDIENTE, PROCESADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long returnId;
    public double monto;

    @Enumerated(EnumType.STRING)
    public Status estado;

    public Instant fecha;

    public Refund() {}

    public Refund(Long returnId, double monto, Status estado, Instant fecha) {
        this.returnId = returnId;
        this.monto = monto;
        this.estado = estado;
        this.fecha = fecha;
    }
}
