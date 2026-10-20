package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/** Giro de la ruleta del día: una persona (usuario o invitado) gira una sola vez por día. */
@Entity
@Table(name = "roulette_spins", uniqueConstraints = @UniqueConstraint(columnNames = {"ownerKey", "day"}))
public class RouletteSpin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Id del usuario, o "guest:&lt;id&gt;" para un invitado. */
    @Column(nullable = false, length = 100)
    public String ownerKey;

    @Column(nullable = false)
    public LocalDate day;

    /** Categoría que salió sorteada (10 % de descuento ese día). */
    @Column(nullable = false)
    public String categoryId;

    public RouletteSpin() {}

    public RouletteSpin(String ownerKey, LocalDate day, String categoryId) {
        this.ownerKey = ownerKey;
        this.day = day;
        this.categoryId = categoryId;
    }
}
