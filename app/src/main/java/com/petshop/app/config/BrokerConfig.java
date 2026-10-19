package com.petshop.app.config;

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.artemis.autoconfigure.ArtemisConfigurationCustomizer;
import org.springframework.boot.artemis.autoconfigure.ArtemisProperties;
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * Broker Artemis embebido con seguridad activa y dos identidades: la que publica (JmsTemplate) solo puede enviar y la
 * que consume (los listeners) solo puede recibir. Ver {@link BrokerSecurity}.
 */
@Configuration
public class BrokerConfig {

    @Bean
    public ArtemisConfigurationCustomizer brokerSecurityCustomizer() {
        return BrokerSecurity::apply;
    }

    /** Instala el gestor de seguridad en el broker que arma Spring Boot, antes de que arranque. */
    @Bean
    public static BeanPostProcessor brokerSecurityManagerInstaller(ObjectProvider<BrokerCredentials> credentials) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof EmbeddedActiveMQ broker) {
                    broker.setSecurityManager(BrokerSecurity.manager(credentials.getObject().accounts()));
                }
                return bean;
            }
        };
    }

    /**
     * Publicación: usuario con rol productor. En modo embebido Spring Boot no aplica usuario ni contraseña a su conexión
     * (quedaría anónima), así que el JmsTemplate usa una conexión propia. No se expone como bean de tipo ConnectionFactory
     * porque eso desactivaría la configuración automática del broker.
     */
    @Bean
    public JmsTemplate jmsTemplate(BrokerCredentials credentials, ArtemisProperties properties) {
        return new JmsTemplate(
                new ActiveMQConnectionFactory(inVmUrl(properties), credentials.producerUser(), credentials.producerPassword()));
    }

    /** Conexión de consumo (listeners): usuario con rol consumidor. */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(DefaultJmsListenerContainerFactoryConfigurer configurer,
                                                                          BrokerCredentials credentials,
                                                                          ArtemisProperties properties) {
        ActiveMQConnectionFactory consumerConnections =
                new ActiveMQConnectionFactory(inVmUrl(properties), credentials.consumerUser(), credentials.consumerPassword());
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, consumerConnections);
        return factory;
    }

    private static String inVmUrl(ArtemisProperties properties) {
        return "vm://" + properties.getEmbedded().getServerId();
    }
}
