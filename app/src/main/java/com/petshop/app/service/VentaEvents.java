package com.petshop.app.service;

public final class VentaEvents {

    private VentaEvents() {}

    public record VentaConfirmada(Long ventaId, String userId, double total) {}

    public record VentaCancelada(Long ventaId, String userId, String motivo) {}

    public record PagoFallido(Long ventaId, String userId, String idTransaccion, int intentos) {}
}
