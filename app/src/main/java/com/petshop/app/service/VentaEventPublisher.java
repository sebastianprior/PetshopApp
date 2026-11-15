package com.petshop.app.service;

import com.petshop.app.model.OutboxEvent;
import com.petshop.app.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Puente entre los eventos de dominio de VentaService y el broker JMS. Cada evento se guarda primero en la tabla
 * outbox (con un identificador único) y {@link OutboxRelay} lo entrega a su cola con reintentos: si el broker está caído
 * o se reinicia, el evento espera en la base de datos y no se pierde. Si ni siquiera se puede guardar, la venta no se
 * ve afectada (solo se registra el error).
 */
@Component
public class VentaEventPublisher {

    public static final String VENTAS_CONFIRMADAS = "ventas.confirmadas";
    public static final String VENTAS_CANCELADAS = "ventas.canceladas";
    public static final String PAGOS_FALLIDOS = "pagos.fallidos";

    private static final Logger LOG = LoggerFactory.getLogger(VentaEventPublisher.class);

    private final OutboxEventRepository outbox;
    private final JsonMapper json;

    public VentaEventPublisher(OutboxEventRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @EventListener
    public void onVentaConfirmada(VentaEvents.VentaConfirmada event) {
        encolar(VENTAS_CONFIRMADAS, event);
    }

    @EventListener
    public void onVentaCancelada(VentaEvents.VentaCancelada event) {
        encolar(VENTAS_CANCELADAS, event);
    }

    @EventListener
    public void onPagoFallido(VentaEvents.PagoFallido event) {
        encolar(PAGOS_FALLIDOS, event);
    }

    private void encolar(String cola, Object evento) {
        try {
            outbox.save(new OutboxEvent(UUID.randomUUID().toString(), cola, json.writeValueAsString(evento), Instant.now()));
        } catch (RuntimeException e) {
            LOG.error("No se pudo guardar {} para la cola {}", evento, cola, e);
        }
    }
}
