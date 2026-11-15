package com.petshop.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "coupons")
public class Coupon {

    public enum DiscountType { PERCENTAGE, FIXED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String code;

    @Enumerated(EnumType.STRING)
    public DiscountType discountType;

    public double discountValue;

    @Column(nullable = false)
    @ColumnDefault("true")
    public boolean active = true;

    public double minPurchase;

    public Integer maxUses;

    @Column(nullable = false)
    @ColumnDefault("0")
    public int usesCount = 0;

    public Instant expiresAt;

    public Coupon() {}

    public Coupon(String code, DiscountType discountType, double discountValue, double minPurchase, Integer maxUses, Instant expiresAt) {
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minPurchase = minPurchase;
        this.maxUses = maxUses;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean hasUsesLeft() {
        return maxUses == null || usesCount < maxUses;
    }

    public double computeDiscount(double subtotal) {
        if (discountType == DiscountType.PERCENTAGE) {
            return subtotal * (discountValue / 100.0);
        }
        return Math.min(discountValue, subtotal);
    }
}
