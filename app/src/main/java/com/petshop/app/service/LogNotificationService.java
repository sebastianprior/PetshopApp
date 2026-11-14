package com.petshop.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Canal de notificaciones simulado: no hay servidor de correo, así que los avisos se registran con el email enmascarado
 * y los mensajes con secretos se guardan como archivos en una "casilla" local (outbox), que hace de bandeja de entrada.
 * En producción esto se reemplaza por un envío SMTP real.
 */
@Component
public class LogNotificationService implements NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger("notificationLogger");

    private final Path outboxDir;

    public LogNotificationService(@Value("${petshop.mail.outbox-dir:outbox}") String outboxDir) {
        this.outboxDir = Path.of(outboxDir);
    }

    @Override
    public void notify(String userEmail, String message) {
        LOG.info("[SIMULATED NOTIFICATION] to {} | {}", mask(userEmail), message);
    }

    @Override
    public void sendSecret(String userEmail, String subject, String secretBody) {
        try {
            Files.createDirectories(outboxDir);
            Path file = outboxDir.resolve(Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8) + ".txt");
            Files.writeString(file, "Para: " + userEmail + "\nAsunto: " + subject + "\n\n" + secretBody + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el mensaje simulado", e);
        }
        LOG.info("[SIMULATED EMAIL] to {} | {} (el contenido se entregó en la casilla simulada)", mask(userEmail), subject);
    }

    static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(sin email)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
