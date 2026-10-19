package com.petshop.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.petshop.app.model.Order;

import java.time.Instant;
import java.util.List;

/**
 * Vista pública de un pedido para /api/orders. Nunca incluye el token del medio de pago ni los reintentos internos.
 * Los campos de {@code JsonInclude.NON_NULL} (comprador, envío y transacción) solo se completan para administradores.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDTO {

    public static class ItemDTO {
        public String productId;
        public int quantity;
        public double price;
        public String variant;
        public Long variantId;
    }

    public Long id;
    public Instant fecha;
    public List<ItemDTO> items;
    public double subtotal;
    public double shippingCost;
    public double discountAmount;
    public String couponCode;
    public double total;
    public String estado;
    public String estadoPago;

    // Solo para administradores
    public String userId;
    public String idTransaccion;
    public String shippingName;
    public String shippingAddress;
    public String shippingCity;
    public String shippingPostalCode;
    public String shippingPhone;

    public static OrderDTO forCustomer(Order order) {
        return base(order);
    }

    public static OrderDTO forAdmin(Order order) {
        OrderDTO dto = base(order);
        dto.userId = order.userId;
        dto.idTransaccion = order.idTransaccion;
        dto.shippingName = order.shippingName;
        dto.shippingAddress = order.shippingAddress;
        dto.shippingCity = order.shippingCity;
        dto.shippingPostalCode = order.shippingPostalCode;
        dto.shippingPhone = order.shippingPhone;
        return dto;
    }

    private static OrderDTO base(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.id = order.id;
        dto.fecha = order.fecha;
        dto.subtotal = order.subtotal;
        dto.shippingCost = order.shippingCost;
        dto.discountAmount = order.discountAmount;
        dto.couponCode = order.couponCode;
        dto.total = order.total;
        dto.estado = order.estado;
        dto.estadoPago = order.estadoPago;
        dto.items = order.items.stream().map(item -> {
            ItemDTO i = new ItemDTO();
            i.productId = item.productId;
            i.quantity = item.quantity;
            i.price = item.price;
            i.variant = item.variant;
            i.variantId = item.variantId;
            return i;
        }).toList();
        return dto;
    }
}
