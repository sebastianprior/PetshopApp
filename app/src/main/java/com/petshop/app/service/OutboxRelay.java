package com.petshop.app.service;

import com.petshop.app.model.OutboxEvent;
import com.petshop.app.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Entrega al broker los eventos guardados en la tabla outbox, del más viejo al más nuevo. Si el broker no responde corta
 * la ronda (para no saltearse el orden), deja el evento pendiente con el error y lo vuelve a intentar en la próxima:
 * cuando el broker vuelve, el sistema se recupera solo y no se pierde ningún evento.
 */
@Component
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final JmsTemplate jms;

    public OutboxRelay(OutboxEventRepository outbox, JmsTemplate jms) {
        this.outbox = outbox;
        this.jms = jms;
    }

    @Scheduled(fixedDelayString = "${petshop.outbox.relay-delay-ms:2000}", initialDelayString = "${petshop.outbox.relay-delay-ms:2000}")
    public void programado() {
        entregarPendientes();
    }

    /** Intenta entregar los eventos pendientes y devuelve cuántos llegaron al broker. */
    public int entregarPendientes() {
        List<OutboxEvent> pendientes = outbox.findTop50BySentAtIsNullOrderByIdAsc();
        int entregados = 0;
        for (OutboxEvent event : pendientes) {
            try {
                jms.send(event.queue, session -> {
                    jakarta.jms.TextMessage message = session.createTextMessage(event.payload);
                    message.setStringProperty("eventId", event.eventId);
                    return message;
                });
            } catch (RuntimeException e) {
                event.attempts++;
                event.lastError = abreviar(e.getMessage());
                outbox.save(event);
                LOG.warn("El broker no recibió el evento {} de la cola {} (intento {}): {}. Se reintenta.",
                        event.eventId, event.queue, event.attempts, e.getMessage());
                break;
            }
            event.sentAt = Instant.now();
            event.attempts++;
            event.lastError = null;
            outbox.save(event);
            entregados++;
        }
        return entregados;
    }

    private static String abreviar(String text) {
        if (text == null) {
            return "error sin mensaje";
        }
        return text.length() > 490 ? text.substring(0, 490) : text;
    }
}
