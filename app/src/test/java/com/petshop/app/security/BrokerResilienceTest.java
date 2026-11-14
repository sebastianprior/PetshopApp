package com.petshop.app.security;

import com.petshop.app.config.BrokerCredentials;
import com.petshop.app.config.BrokerSecurity;
import com.petshop.app.service.DlqService;
import jakarta.jms.Connection;
import jakarta.jms.JMSSecurityException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.artemis.autoconfigure.ArtemisProperties;
import org.springframework.jms.core.JmsTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resiliencia del broker real (Artemis, con la misma política que usa la aplicación): un mensaje que falla tres veces
 * termina en la cola de mensajes muertos, desde donde se reprocesa; los mensajes sobreviven a un reinicio del broker; y
 * nadie puede publicar en la cola de mensajes muertos. Todos los datos son ficticios.
 */
class BrokerResilienceTest {

    private static final int SERVER_ID = 74;
    private static final String URL = "vm://" + SERVER_ID;
    private static final String QUEUE = "ventas.confirmadas";
    private static final BrokerCredentials CREDENTIALS = new BrokerCredentials("productor-test", "clave-productor-ficticia",
            "consumidor-test", "clave-consumidor-ficticia");

    @TempDir
    Path data;

    private EmbeddedActiveMQ broker;

    private Configuration configuration(boolean persistent) throws Exception {
        ConfigurationImpl config = new ConfigurationImpl();
        config.addAcceptorConfiguration("in-vm", URL);
        config.setPersistenceEnabled(persistent);
        if (persistent) {
            config.setJournalType(JournalType.NIO);
            config.setBindingsDirectory(data.resolve("bindings").toString());
            config.setJournalDirectory(data.resolve("journal").toString());
            config.setLargeMessagesDirectory(data.resolve("large").toString());
            config.setPagingDirectory(data.resolve("paging").toString());
        }
        for (String name : new String[] {QUEUE, "ventas.canceladas", "pagos.fallidos", BrokerSecurity.DEAD_LETTER_QUEUE}) {
            config.addAddressConfiguration(new CoreAddressConfiguration().setName(name).addRoutingType(RoutingType.ANYCAST)
                    .addQueueConfiguration(QueueConfiguration.of(name).setAddress(name).setRoutingType(RoutingType.ANYCAST).setDurable(persistent)));
        }
        BrokerSecurity.apply(config);
        return config;
    }

    private void start(boolean persistent) throws Exception {
        broker = new EmbeddedActiveMQ().setConfiguration(configuration(persistent))
                .setSecurityManager(BrokerSecurity.manager(CREDENTIALS.accounts()));
        broker.start();
        // Con persistencia, el broker carga el journal al activarse: hay que esperarlo antes de conectar
        assertThat(broker.getActiveMQServer().waitForActivation(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    @BeforeEach
    void startBroker() throws Exception {
        start(false);
    }

    @AfterEach
    void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    private Connection connect(String user, String password) throws Exception {
        Connection connection = new ActiveMQConnectionFactory(URL).createConnection(user, password);
        connection.start();
        return connection;
    }

    private void publish(String text, String eventId) throws Exception {
        try (Connection producer = connect("productor-test", "clave-productor-ficticia")) {
            Session session = producer.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageProducer out = session.createProducer(session.createQueue(QUEUE))) {
                TextMessage message = session.createTextMessage(text);
                message.setStringProperty("eventId", eventId);
                out.send(message);
            }
        }
    }

    /** Un consumidor que recibe el mensaje y falla: hace rollback, como un listener que lanza una excepción. */
    private void failToProcess(int times) throws Exception {
        try (Connection consumer = connect("consumidor-test", "clave-consumidor-ficticia")) {
            for (int i = 0; i < times; i++) {
                Session session = consumer.createSession(true, Session.SESSION_TRANSACTED);
                try (MessageConsumer in = session.createConsumer(session.createQueue(QUEUE))) {
                    assertThat(in.receive(5000)).as("entrega número " + (i + 1)).isNotNull();
                    session.rollback();
                }
            }
        }
    }

    private String receive(String queueName) throws Exception {
        try (Connection consumer = connect("consumidor-test", "clave-consumidor-ficticia")) {
            Session session = consumer.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageConsumer in = session.createConsumer(session.createQueue(queueName))) {
                var message = in.receive(1500);
                return message == null ? null : ((TextMessage) message).getText();
            }
        }
    }

    private DlqService dlq() {
        ArtemisProperties properties = new ArtemisProperties();
        properties.getEmbedded().setServerId(SERVER_ID);
        JmsTemplate producer = new JmsTemplate(new ActiveMQConnectionFactory(URL, "productor-test", "clave-productor-ficticia"));
        return new DlqService(CREDENTIALS, properties, producer);
    }

    @Test
    void aMessageThatFailsThreeTimesEndsUpInTheDeadLetterQueue() throws Exception {
        publish("{\"ventaId\":5}", "evento-5");

        failToProcess(BrokerSecurity.MAX_DELIVERY_ATTEMPTS);

        assertThat(receive(QUEUE)).as("ya no está en la cola original").isNull();
        List<DlqService.DeadLetter> letters = dlq().listar();
        assertThat(letters).hasSize(1);
        assertThat(letters.get(0).originalQueue()).isEqualTo(QUEUE);
        assertThat(letters.get(0).eventId()).isEqualTo("evento-5");
        assertThat(letters.get(0).payload()).isEqualTo("{\"ventaId\":5}");
    }

    @Test
    void aMessageThatFailsOnlyTwiceIsNotDeadLettered() throws Exception {
        publish("{\"ventaId\":6}", "evento-6");

        failToProcess(BrokerSecurity.MAX_DELIVERY_ATTEMPTS - 1);

        assertThat(dlq().contar()).isZero();
        assertThat(receive(QUEUE)).isEqualTo("{\"ventaId\":6}");
    }

    @Test
    void reprocessingPutsTheMessageBackInItsOriginalQueueKeepingItsEventId() throws Exception {
        publish("{\"ventaId\":7}", "evento-7");
        failToProcess(BrokerSecurity.MAX_DELIVERY_ATTEMPTS);
        DlqService dlq = dlq();
        assertThat(dlq.contar()).isEqualTo(1);

        int reprocessed = dlq.reprocesar();

        assertThat(reprocessed).isEqualTo(1);
        assertThat(dlq.contar()).as("la cola de mensajes muertos queda vacía").isZero();
        try (Connection consumer = connect("consumidor-test", "clave-consumidor-ficticia")) {
            Session session = consumer.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageConsumer in = session.createConsumer(session.createQueue(QUEUE))) {
                TextMessage back = (TextMessage) in.receive(2000);
                assertThat(back).isNotNull();
                assertThat(back.getText()).isEqualTo("{\"ventaId\":7}");
                assertThat(back.getStringProperty("eventId")).isEqualTo("evento-7");
            }
        }
    }

    @Test
    void reprocessingWithAnEmptyDeadLetterQueueDoesNothing() {
        assertThat(dlq().reprocesar()).isZero();
        assertThat(dlq().listar()).isEmpty();
    }

    @Test
    void nobodyCanPublishDirectlyIntoTheDeadLetterQueue() throws Exception {
        for (String[] account : new String[][] {{"productor-test", "clave-productor-ficticia"}, {"consumidor-test", "clave-consumidor-ficticia"}}) {
            try (Connection connection = connect(account[0], account[1])) {
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Queue deadLetter = session.createQueue(BrokerSecurity.DEAD_LETTER_QUEUE);
                assertThatThrownBy(() -> {
                    try (MessageProducer out = session.createProducer(deadLetter)) {
                        out.send(session.createTextMessage("intento-no-autorizado"));
                    }
                }).as(account[0]).isInstanceOf(JMSSecurityException.class);
            }
        }
        assertThat(dlq().contar()).isZero();
    }

    @Test
    void theProducerAccountCannotReadTheDeadLetterQueue() throws Exception {
        try (Connection producer = connect("productor-test", "clave-productor-ficticia")) {
            Session session = producer.createSession(false, Session.AUTO_ACKNOWLEDGE);
            assertThatThrownBy(() -> session.createConsumer(session.createQueue(BrokerSecurity.DEAD_LETTER_QUEUE)))
                    .isInstanceOf(JMSSecurityException.class);
        }
    }

    /**
     * Se salta con Java 25 o más: ahí Netty no puede liberar los buffers del journal de Artemis (UnsupportedOperationException
     * "Cannot clean arbitrary ByteBuffer instances", un problema de Netty con esas versiones de Java, ajeno a esta aplicación).
     * El sistema corre en Java 21 (Dockerfile y CI), que es donde esta prueba se ejecuta.
     */
    @Test
    @EnabledForJreRange(max = JRE.JAVA_24)
    void messagesSurviveABrokerRestartWhenThePersistenceIsOn() throws Exception {
        broker.stop();
        start(true);
        publish("{\"ventaId\":8}", "evento-8");

        broker.stop();
        Thread.sleep(1000); // el broker libera el bloqueo de su carpeta de datos al terminar de apagarse
        start(true);

        assertThat(receive(QUEUE)).as("el mensaje seguía en la cola después del reinicio").isEqualTo("{\"ventaId\":8}");
    }

    @Test
    void withoutPersistenceARestartLosesTheMessagesWhichIsWhyTheApplicationTurnsItOn() throws Exception {
        publish("{\"ventaId\":9}", "evento-9");

        broker.stop();
        start(false);

        assertThat(receive(QUEUE)).isNull();
    }
}
