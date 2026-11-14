package com.petshop.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Usuarios del broker: uno que solo publica y otro que solo consume. Las contraseñas vienen de la configuración
 * (variables de entorno) o, si no se definen, se generan al azar en cada arranque: no hay valores fijos en el repositorio.
 */
@Component
public class BrokerCredentials {

    private final String producerUser;
    private final String producerPassword;
    private final String consumerUser;
    private final String consumerPassword;

    public BrokerCredentials(@Value("${petshop.broker.producer-user:petshop-productor}") String producerUser,
                             @Value("${petshop.broker.producer-password:}") String producerPassword,
                             @Value("${petshop.broker.consumer-user:petshop-consumidor}") String consumerUser,
                             @Value("${petshop.broker.consumer-password:}") String consumerPassword) {
        this.producerUser = producerUser;
        this.producerPassword = producerPassword == null || producerPassword.isBlank() ? randomSecret() : producerPassword;
        this.consumerUser = consumerUser;
        this.consumerPassword = consumerPassword == null || consumerPassword.isBlank() ? randomSecret() : consumerPassword;
    }

    public String producerUser() {
        return producerUser;
    }

    public String producerPassword() {
        return producerPassword;
    }

    public String consumerUser() {
        return consumerUser;
    }

    public String consumerPassword() {
        return consumerPassword;
    }

    public Map<String, BrokerSecurity.Account> accounts() {
        return Map.of(
                producerUser, new BrokerSecurity.Account(producerPassword, Set.of(BrokerSecurity.PRODUCER_ROLE)),
                consumerUser, new BrokerSecurity.Account(consumerPassword, Set.of(BrokerSecurity.CONSUMER_ROLE)));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
