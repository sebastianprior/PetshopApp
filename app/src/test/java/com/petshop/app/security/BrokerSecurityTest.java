package com.petshop.app.security;

import com.petshop.app.config.BrokerCredentials;
import com.petshop.app.config.BrokerSecurity;
import jakarta.jms.Connection;
import jakarta.jms.JMSSecurityException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SEC-I02: el broker aplica el mínimo privilegio. Se levanta un broker con la misma política que usa la aplicación
 * (BrokerSecurity) y usuarios ficticios; ningún dato es real.
 */
class BrokerSecurityTest {

    private static final String QUEUE = "ventas.confirmadas";
    private static final String OTHER_QUEUE = "pagos.fallidos";
    private static final String URL = "vm://73";

    private static EmbeddedActiveMQ broker;
    private static final BrokerCredentials CREDENTIALS = new BrokerCredentials("productor-test", "clave-productor-ficticia",
            "consumidor-test", "clave-consumidor-ficticia");

    @BeforeAll
    static void startBroker() throws Exception {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setPersistenceEnabled(false);
        config.addAcceptorConfiguration("in-vm", URL);
        for (String name : new String[] {QUEUE, OTHER_QUEUE}) {
            config.addAddressConfiguration(new CoreAddressConfiguration().setName(name).addRoutingType(RoutingType.ANYCAST)
                    .addQueueConfiguration(QueueConfiguration.of(name).setAddress(name).setRoutingType(RoutingType.ANYCAST).setDurable(false)));
        }
        BrokerSecurity.apply(config);
        broker = new EmbeddedActiveMQ().setConfiguration(config).setSecurityManager(BrokerSecurity.manager(CREDENTIALS.accounts()));
        broker.start();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        broker.stop();
    }

    private Connection connect(String user, String password) throws Exception {
        Connection connection = new ActiveMQConnectionFactory(URL).createConnection(user, password);
        connection.start();
        return connection;
    }

    private void publish(Connection connection, String queueName, String text) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(queueName);
        try (MessageProducer producer = session.createProducer(queue)) {
            producer.send(session.createTextMessage(text));
        }
    }

    private String receive(Connection connection, String queueName) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        try (MessageConsumer consumer = session.createConsumer(session.createQueue(queueName))) {
            var message = consumer.receive(1500);
            return message == null ? null : ((jakarta.jms.TextMessage) message).getText();
        }
    }

    @Test
    void aConsumerOnlyUserCannotPublishAndNothingEntersTheQueue() throws Exception {
        try (Connection consumer = connect("consumidor-test", "clave-consumidor-ficticia")) {
            assertThatThrownBy(() -> publish(consumer, QUEUE, "intento-no-autorizado")).isInstanceOf(JMSSecurityException.class);
            assertThat(receive(consumer, QUEUE)).isNull();
        }
    }

    @Test
    void theProducerCanPublishAndTheConsumerReceives() throws Exception {
        try (Connection producer = connect("productor-test", "clave-productor-ficticia");
             Connection consumer = connect("consumidor-test", "clave-consumidor-ficticia")) {
            publish(producer, OTHER_QUEUE, "mensaje-legitimo");

            assertThat(receive(consumer, OTHER_QUEUE)).isEqualTo("mensaje-legitimo");
        }
    }

    @Test
    void theProducerCannotConsume() throws Exception {
        try (Connection producer = connect("productor-test", "clave-productor-ficticia")) {
            assertThatThrownBy(() -> receive(producer, QUEUE)).isInstanceOf(JMSSecurityException.class);
        }
    }

    @Test
    void unknownUsersWrongPasswordsAndAnonymousConnectionsAreRejected() {
        assertThatThrownBy(() -> connect("intruso-ficticio", "clave-incorrecta")).isInstanceOf(JMSSecurityException.class);
        assertThatThrownBy(() -> connect("productor-test", "clave-incorrecta")).isInstanceOf(JMSSecurityException.class);
        assertThatThrownBy(() -> new ActiveMQConnectionFactory(URL).createConnection()).isInstanceOf(JMSSecurityException.class);
    }

    @Test
    void credentialsAreGeneratedRandomlyWhenNotConfigured() {
        BrokerCredentials first = new BrokerCredentials("a", "", "b", "");
        BrokerCredentials second = new BrokerCredentials("a", "", "b", "");

        assertThat(first.producerPassword()).isNotBlank().isNotEqualTo(second.producerPassword());
        assertThat(first.consumerPassword()).isNotBlank().isNotEqualTo(first.producerPassword());
    }
}
