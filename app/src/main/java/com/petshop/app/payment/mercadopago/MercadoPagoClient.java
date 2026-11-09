package com.petshop.app.payment.mercadopago;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lo que la tienda necesita de MercadoPago (Checkout Pro): crear el link de pago, consultar pagos y devolver dinero. */
public interface MercadoPagoClient {

    /** ¿Hay un Access Token configurado? Sin él, MercadoPago no se ofrece. */
    boolean isEnabled();

    /** Crea el link de pago (preferencia) de un pedido. */
    Preference createPreference(PreferenceRequest request) throws MercadoPagoException;

    /** Pagos registrados para un pedido (se buscan por la referencia externa que se mandó al crear el link). */
    List<Payment> paymentsOf(String externalReference) throws MercadoPagoException;

    Optional<Payment> payment(String paymentId) throws MercadoPagoException;

    /** Devuelve el dinero de un pago aprobado (reembolso total). */
    void refund(String paymentId) throws MercadoPagoException;

    record PreferenceRequest(String externalReference, String title, double amount, String backUrl, String notificationUrl,
                             boolean autoReturn, Instant expiresAt) {}

    /** Link de pago: {@code initPoint} es la dirección a la que se manda al cliente. */
    record Preference(String id, String initPoint) {}

    /** Estado de un pago según MercadoPago: approved, pending, in_process, rejected, cancelled, refunded… */
    record Payment(String id, String status, double amount, String externalReference) {}

    class MercadoPagoException extends Exception {
        public MercadoPagoException(String message) {
            super(message);
        }

        public MercadoPagoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
