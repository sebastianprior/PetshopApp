package com.petshop.app.dto;

import com.petshop.app.model.Order;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public class VentaDTO {

    public static class ItemDTO {
        public String productId;
        public String name;
        public String variant;
        public Long variantId;
        public int quantity;
        public double price;
    }

    public Long id;
    public String estado;
    public String estadoPago;
    public String idTransaccion;
    public Instant fecha;
    public List<ItemDTO> items;
    public double subtotal;
    public double shippingCost;
    public double discountAmount;
    public String couponCode;
    public double total;

    public static VentaDTO from(Order order, Function<String, String> productName) {
        VentaDTO dto = new VentaDTO();
        dto.id = order.id;
        dto.estado = order.estado;
        dto.estadoPago = order.estadoPago;
        dto.idTransaccion = order.idTransaccion;
        dto.fecha = order.fecha;
        dto.subtotal = order.subtotal;
        dto.shippingCost = order.shippingCost;
        dto.discountAmount = order.discountAmount;
        dto.couponCode = order.couponCode;
        dto.total = order.total;
        dto.items = order.items.stream().map(item -> {
            ItemDTO i = new ItemDTO();
            i.productId = item.productId;
            i.name = productName.apply(item.productId);
            i.variant = item.variant;
            i.variantId = item.variantId;
            i.quantity = item.quantity;
            i.price = item.price;
            return i;
        }).toList();
        return dto;
    }
}
