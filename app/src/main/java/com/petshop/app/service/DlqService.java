package com.petshop.app.service;

import com.petshop.app.config.BrokerCredentials;
import com.petshop.app.config.BrokerSecurity;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.artemis.autoconfigure.ArtemisProperties;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cola de mensajes muertos: cuenta los mensajes que el consumidor no pudo procesar y permite reprocesarlos, es decir,
 * devolverlos a la cola de la que vinieron. Se lee con la cuenta consumidora y se republica con la productora, cada una
 * con sus propios permisos. Como el consumidor descarta los duplicados por {@code eventId}, reprocesar dos veces el mismo
 * mensaje no repite sus efectos.
 */
@Service
public class DlqService {

    static final String ORIGINAL_ADDRESS = "_AMQ_ORIG_ADDRESS";
    private static final int MAX_PER_RUN = 100;
    private static final Logger LOG = LoggerFactory.getLogger(DlqService.class);

    private final JmsTemplate consumer;
    private final JmsTemplate producer;

    public DlqService(BrokerCredentials credentials, ArtemisProperties properties, JmsTemplate producer) {
        String url = "vm://" + properties.getEmbedded().getServerId();
        this.consumer = new JmsTemplate(new ActiveMQConnectionFactory(url, credentials.consumerUser(), credentials.consumerPassword()));
        this.consumer.setReceiveTimeout(500);
        this.producer = producer;
    }

    /** Un mensaje de la cola de mensajes muertos, sin sacarlo de ahí. */
    public record DeadLetter(String messageId, String originalQueue, String eventId, String payload) {}

    /** Los mensajes que hay hoy en la cola de mensajes muertos (hasta 100). */
    public List<DeadLetter> listar() {
        List<DeadLetter> result = consumer.browse(BrokerSecurity.DEAD_LETTER_QUEUE, (session, browser) -> {
            List<DeadLetter> found = new ArrayList<>();
            var messages = browser.getEnumeration();
            while (messages.hasMoreElements() && found.size() < MAX_PER_RUN) {
                if (messages.nextElement() instanceof TextMessage text) {
                    found.add(new DeadLetter(text.getJMSMessageID(), text.getStringProperty(ORIGINAL_ADDRESS),
                            text.getStringProperty("eventId"), text.getText()));
                }
            }
            return found;
        });
        return result == null ? Collections.emptyList() : result;
    }

    public int contar() {
        return listar().size();
    }

    /**
     * Devuelve cada mensaje a su cola de origen y recién después lo saca de la cola de mensajes muertos: si algo falla en
     * el medio, el mensaje queda en la cola de mensajes muertos (nunca se pierde) y un reproceso repetido no duplica sus
     * efectos. Devuelve cuántos se reprocesaron.
     */
    public int reprocesar() {
        int moved = 0;
        for (DeadLetter letter : listar()) {
            if (letter.originalQueue() == null || letter.originalQueue().isBlank()) {
                LOG.warn("El mensaje {} de la cola de mensajes muertos no dice de qué cola vino: se deja ahí", letter.messageId());
                continue;
            }
            producer.send(letter.originalQueue(), session -> {
                TextMessage message = session.createTextMessage(letter.payload());
                if (letter.eventId() != null) {
                    message.setStringProperty("eventId", letter.eventId());
                }
                return message;
            });
            consumer.receiveSelected(BrokerSecurity.DEAD_LETTER_QUEUE, "JMSMessageID = '" + letter.messageId() + "'");
            LOG.info("Mensaje {} (evento {}) devuelto a la cola {}", letter.messageId(), letter.eventId(), letter.originalQueue());
            moved++;
        }
        return moved;
    }
}
