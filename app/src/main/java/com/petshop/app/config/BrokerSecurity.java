package com.petshop.app.config;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager5;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;

import javax.security.auth.Subject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seguridad del broker de mensajería con mínimo privilegio: el productor solo puede publicar, el consumidor solo puede
 * consumir y cualquier otro usuario (o sin credenciales) queda rechazado. Se aplica a las colas de ventas y de pagos.
 */
public final class BrokerSecurity {

    public static final String PRODUCER_ROLE = "productor";
    public static final String CONSUMER_ROLE = "consumidor";

    /** Cola de mensajes muertos: acá termina un mensaje que el consumidor no logró procesar tras todos los intentos. */
    public static final String DEAD_LETTER_QUEUE = "DLQ";
    /** Entregas de un mismo mensaje antes de mandarlo a la cola de mensajes muertos. */
    public static final int MAX_DELIVERY_ATTEMPTS = 3;
    /** Espera entre un intento y el siguiente. */
    public static final long REDELIVERY_DELAY_MS = 1000;

    private static final List<String> PROTECTED_ADDRESSES = List.of("ventas.#", "pagos.#");

    private BrokerSecurity() {}

    /**
     * Activa la seguridad, define qué rol puede hacer qué en cada dirección protegida y la política de reintentos: un
     * mensaje que falla {@link #MAX_DELIVERY_ATTEMPTS} veces se mueve a la cola {@link #DEAD_LETTER_QUEUE}.
     */
    public static void apply(Configuration configuration) {
        configuration.setSecurityEnabled(true);
        Role producer = new Role(PRODUCER_ROLE, true, false, false, false, false, false, false, false, false, false, false, false);
        Role consumer = new Role(CONSUMER_ROLE, false, true, false, false, false, false, false, false, false, false, false, false);
        for (String match : PROTECTED_ADDRESSES) {
            configuration.putSecurityRoles(match, Set.of(producer, consumer));
            configuration.addAddressSetting(match, new AddressSettings()
                    .setMaxDeliveryAttempts(MAX_DELIVERY_ATTEMPTS)
                    .setRedeliveryDelay(REDELIVERY_DELAY_MS)
                    .setDeadLetterAddress(SimpleString.of(DEAD_LETTER_QUEUE)));
        }
        // La cola de mensajes muertos solo la lee (y recorre) el consumidor; nadie más publica ahí: el broker mueve los mensajes
        Role deadLetterReader = new Role(CONSUMER_ROLE, false, true, false, false, false, false, false, true, false, false, false, false);
        configuration.putSecurityRoles(DEAD_LETTER_QUEUE, Set.of(deadLetterReader));
    }

    /** Usuario del broker: contraseña y roles. */
    public record Account(String password, Set<String> roles) {}

    public static ActiveMQSecurityManager5 manager(Map<String, Account> accounts) {
        return new Manager(accounts);
    }

    private static final class Manager implements ActiveMQSecurityManager5 {

        private final Map<String, Account> accounts;

        private Manager(Map<String, Account> accounts) {
            this.accounts = Map.copyOf(accounts);
        }

        @Override
        public Subject authenticate(String user, String password, RemotingConnection remotingConnection, String securityDomain) {
            Account account = user == null ? null : accounts.get(user);
            if (account == null || password == null || !sameSecret(account.password(), password)) {
                return null;
            }
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(user));
            account.roles().forEach(role -> subject.getPrincipals().add(new RolePrincipal(role)));
            return subject;
        }

        @Override
        public boolean authorize(Subject subject, Set<Role> roles, CheckType checkType, String address) {
            if (subject == null) {
                return false;
            }
            Set<String> held = subject.getPrincipals(RolePrincipal.class).stream()
                    .map(RolePrincipal::getName)
                    .collect(Collectors.toSet());
            return roles.stream().anyMatch(role -> held.contains(role.getName()) && checkType.hasRole(role));
        }

        @Override
        public boolean validateUser(String user, String password) {
            Account account = user == null ? null : accounts.get(user);
            return account != null && password != null && sameSecret(account.password(), password);
        }

        @Override
        public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            if (!validateUser(user, password)) {
                return false;
            }
            Set<String> held = accounts.get(user).roles();
            return roles.stream().anyMatch(role -> held.contains(role.getName()) && checkType.hasRole(role));
        }

        private static boolean sameSecret(String expected, String given) {
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
        }
    }
}
