package com.petshop.app.service;

import com.petshop.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Consumidor de las colas de ventas: notifica al cliente y deja registro de los pagos que fallaron. */
@Component
public class VentaEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(VentaEventListener.class);

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final JsonMapper json;

    public VentaEventListener(UserRepository userRepository, NotificationService notificationService, JsonMapper json) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.json = json;
    }

    @JmsListener(destination = VentaEventPublisher.VENTAS_CONFIRMADAS)
    public void onVentaConfirmada(String message) {
        VentaEvents.VentaConfirmada event = json.readValue(message, VentaEvents.VentaConfirmada.class);
        LOG.info("[COLA {}] VentaConfirmada ventaId={} total={}", VentaEventPublisher.VENTAS_CONFIRMADAS, event.ventaId(), event.total());
        notificar(event.userId(), "Tu compra #" + event.ventaId() + " fue confirmada. Total: $" + event.total());
    }

    @JmsListener(destination = VentaEventPublisher.VENTAS_CANCELADAS)
    public void onVentaCancelada(String message) {
        VentaEvents.VentaCancelada event = json.readValue(message, VentaEvents.VentaCancelada.class);
        LOG.info("[COLA {}] VentaCancelada ventaId={} motivo={}", VentaEventPublisher.VENTAS_CANCELADAS, event.ventaId(), event.motivo());
        notificar(event.userId(), "Tu compra #" + event.ventaId() + " fue cancelada: " + event.motivo());
    }

    @JmsListener(destination = VentaEventPublisher.PAGOS_FALLIDOS)
    public void onPagoFallido(String message) {
        VentaEvents.PagoFallido event = json.readValue(message, VentaEvents.PagoFallido.class);
        LOG.error("[DLQ {}] venta={} idTransaccion={} intentos={}", VentaEventPublisher.PAGOS_FALLIDOS,
                event.ventaId(), event.idTransaccion(), event.intentos());
    }

    private void notificar(String userId, String text) {
        userRepository.findById(userId).ifPresent(user -> notificationService.notify(user.email, text));
    }
}
