package com.petshop.app.service;

import com.petshop.app.repository.ProcessedEventRepository;
import com.petshop.app.repository.UserRepository;
import com.petshop.app.model.ProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * Consumidor de las colas de ventas: notifica al cliente y deja registro de los pagos que fallaron.
 *
 * <p>Es idempotente: cada mensaje trae un {@code eventId} y, si ya se procesó, se descarta (el broker puede entregar un
 * mensaje más de una vez). Si el procesamiento falla (por ejemplo, un mensaje inválido) se lanza la excepción: el broker
 * reintenta y, agotados los intentos, mueve el mensaje a la cola de mensajes muertos {@code DLQ}, desde donde un
 * administrador puede reprocesarlo.</p>
 */
@Component
public class VentaEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(VentaEventListener.class);

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final JsonMapper json;
    private final ProcessedEventRepository processedEvents;

    public VentaEventListener(UserRepository userRepository, NotificationService notificationService, JsonMapper json,
                              ProcessedEventRepository processedEvents) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.json = json;
        this.processedEvents = processedEvents;
    }

    @JmsListener(destination = VentaEventPublisher.VENTAS_CONFIRMADAS)
    public void onVentaConfirmada(String message, @Header(name = "eventId", required = false) String eventId) {
        procesarUnaVez(VentaEventPublisher.VENTAS_CONFIRMADAS, eventId, () -> {
            VentaEvents.VentaConfirmada event = json.readValue(message, VentaEvents.VentaConfirmada.class);
            LOG.info("[COLA {}] VentaConfirmada ventaId={} total={}", VentaEventPublisher.VENTAS_CONFIRMADAS, event.ventaId(), event.total());
            notificar(event.userId(), "Tu compra #" + event.ventaId() + " fue confirmada. Total: $" + event.total());
        });
    }

    @JmsListener(destination = VentaEventPublisher.VENTAS_CANCELADAS)
    public void onVentaCancelada(String message, @Header(name = "eventId", required = false) String eventId) {
        procesarUnaVez(VentaEventPublisher.VENTAS_CANCELADAS, eventId, () -> {
            VentaEvents.VentaCancelada event = json.readValue(message, VentaEvents.VentaCancelada.class);
            LOG.info("[COLA {}] VentaCancelada ventaId={} motivo={}", VentaEventPublisher.VENTAS_CANCELADAS, event.ventaId(), event.motivo());
            notificar(event.userId(), "Tu compra #" + event.ventaId() + " fue cancelada: " + event.motivo());
        });
    }

    @JmsListener(destination = VentaEventPublisher.PAGOS_FALLIDOS)
    public void onPagoFallido(String message, @Header(name = "eventId", required = false) String eventId) {
        procesarUnaVez(VentaEventPublisher.PAGOS_FALLIDOS, eventId, () -> {
            VentaEvents.PagoFallido event = json.readValue(message, VentaEvents.PagoFallido.class);
            LOG.error("[PAGO FALLIDO {}] venta={} idTransaccion={} intentos={}", VentaEventPublisher.PAGOS_FALLIDOS,
                    event.ventaId(), event.idTransaccion(), event.intentos());
        });
    }

    private void procesarUnaVez(String queue, String eventId, Runnable work) {
        if (eventId != null && processedEvents.existsById(eventId)) {
            LOG.info("[COLA {}] el evento {} ya se procesó: se descarta el duplicado", queue, eventId);
            return;
        }
        try {
            work.run();
        } catch (RuntimeException e) {
            LOG.error("[COLA {}] no se pudo procesar el evento {}: {}. El broker lo reintenta y, agotados los intentos, "
                    + "lo mueve a {}", queue, eventId, e.getMessage(), com.petshop.app.config.BrokerSecurity.DEAD_LETTER_QUEUE);
            throw e;
        }
        if (eventId != null) {
            processedEvents.save(new ProcessedEvent(eventId, queue, Instant.now()));
        }
    }

    private void notificar(String userId, String text) {
        userRepository.findById(userId).ifPresent(user -> notificationService.notify(user.email, text));
    }
}
