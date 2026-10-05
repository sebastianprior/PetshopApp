package com.petshop.app.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import org.hibernate.annotations.ColumnDefault;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String userId;
    public Instant fecha;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    public List<OrderItem> items = new ArrayList<>();

    public double subtotal;
    public double shippingCost;
    public double discountAmount;
    public String couponCode;
    public double total;
    public String estado;

    public String idTransaccion;
    public String estadoPago;
    public String medioPagoToken;

    @Column(nullable = false)
    @ColumnDefault("0")
    public int intentosPago = 0;

    public String shippingName;
    public String shippingAddress;
    public String shippingCity;
    public String shippingPostalCode;
    public String shippingPhone;

    public Order() {}

    public Order(String userId, Instant fecha, List<OrderItem> items, double total, String estado) {
        this.userId = userId;
        this.fecha = fecha;
        this.items = items;
        this.total = total;
        this.estado = estado;
    }

    @Embeddable
    public static class OrderItem {
        public String productId;
        public int quantity;
        public double price;
        public String variant;
        public Long variantId;

        public OrderItem() {}

        public OrderItem(String productId, int quantity, double price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }

        public OrderItem(String productId, int quantity, double price, String variant, Long variantId) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
            this.variant = variant;
            this.variantId = variantId;
        }
    }
}
