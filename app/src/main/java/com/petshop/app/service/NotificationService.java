package com.petshop.app.service;

public interface NotificationService {

    /** Aviso sin datos sensibles; puede quedar en los logs. */
    void notify(String userEmail, String message);

    /**
     * Mensaje cuyo cuerpo contiene un secreto (por ejemplo, el código de recuperación de contraseña).
     * El cuerpo solo debe llegar al destinatario: nunca a los logs ni a la respuesta de la API.
     */
    void sendSecret(String userEmail, String subject, String secretBody);
}
