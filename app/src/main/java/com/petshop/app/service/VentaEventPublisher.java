package com.petshop.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puente entre los eventos de dominio de VentaService y el broker JMS: cada evento se publica como JSON en su cola.
 * Si el broker falla, la venta no se ve afectada (solo se registra el error).
 */
@Component
public class VentaEventPublisher {

    public static final String VENTAS_CONFIRMADAS = "ventas.confirmadas";
    public static final String VENTAS_CANCELADAS = "ventas.canceladas";
    public static final String PAGOS_FALLIDOS = "pagos.fallidos";

    private static final Logger LOG = LoggerFactory.getLogger(VentaEventPublisher.class);

    private final JmsTemplate jms;
    private final JsonMapper json;

    public VentaEventPublisher(JmsTemplate jms, JsonMapper json) {
        this.jms = jms;
        this.json = json;
    }

    @EventListener
    public void onVentaConfirmada(VentaEvents.VentaConfirmada event) {
        enviar(VENTAS_CONFIRMADAS, event);
    }

    @EventListener
    public void onVentaCancelada(VentaEvents.VentaCancelada event) {
        enviar(VENTAS_CANCELADAS, event);
    }

    @EventListener
    public void onPagoFallido(VentaEvents.PagoFallido event) {
        enviar(PAGOS_FALLIDOS, event);
    }

    private void enviar(String cola, Object evento) {
        try {
            jms.convertAndSend(cola, json.writeValueAsString(evento));
        } catch (RuntimeException e) {
            LOG.error("No se pudo publicar {} en la cola {}", evento, cola, e);
        }
    }
}
