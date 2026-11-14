package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

/** Refresh token opaco: en la base solo se guarda su hash SHA-256, nunca el valor que recibe el cliente. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    public String tokenHash;

    @Column(nullable = false)
    public String userId;

    public long expiryEpochMillis;

    @Column(nullable = false)
    @ColumnDefault("false")
    public boolean revoked;

    public RefreshToken() {}

    public RefreshToken(String tokenHash, String userId, long expiryEpochMillis) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiryEpochMillis = expiryEpochMillis;
    }
}
