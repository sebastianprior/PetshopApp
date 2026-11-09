package com.petshop.app.payment.mercadopago;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuración de los pagos: MercadoPago y los medios simulados. Todo sale de variables de entorno. */
@Component
public class MercadoPagoSettings {

    private final String accessToken;
    private final String apiUrl;
    private final int pendingExpiryMinutes;
    private final String frontendUrl;
    private final String publicApiUrl;
    private final boolean simulatedEnabled;

    public MercadoPagoSettings(@Value("${petshop.mercadopago.access-token:}") String accessToken,
                               @Value("${petshop.mercadopago.api-url:https://api.mercadopago.com}") String apiUrl,
                               @Value("${petshop.mercadopago.pending-expiry-minutes:30}") int pendingExpiryMinutes,
                               @Value("${petshop.frontend-url:http://localhost:5173}") String frontendUrl,
                               @Value("${petshop.public-api-url:}") String publicApiUrl,
                               @Value("${petshop.payments.simulated-enabled:true}") boolean simulatedEnabled) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.apiUrl = stripTrailingSlash(apiUrl);
        this.pendingExpiryMinutes = Math.max(1, pendingExpiryMinutes);
        this.frontendUrl = stripTrailingSlash(frontendUrl);
        this.publicApiUrl = publicApiUrl == null ? "" : stripTrailingSlash(publicApiUrl.trim());
        this.simulatedEnabled = simulatedEnabled;
    }

    /** Access Token (secreto): nunca se muestra ni se registra en los logs. */
    public String accessToken() {
        return accessToken;
    }

    public String apiUrl() {
        return apiUrl;
    }

    /** Minutos que un pedido espera el pago antes de cancelarse y liberar su stock. */
    public int pendingExpiryMinutes() {
        return pendingExpiryMinutes;
    }

    /** Dirección de la tienda: a donde MercadoPago devuelve al cliente después de pagar. */
    public String frontendUrl() {
        return frontendUrl;
    }

    /** Dirección pública del backend, para que MercadoPago avise los pagos (webhook). Vacía en desarrollo local. */
    public String publicApiUrl() {
        return publicApiUrl;
    }

    /** ¿Se aceptan los medios de pago de prueba (tok_aprobado…)? En producción tiene que estar apagado. */
    public boolean simulatedEnabled() {
        return simulatedEnabled;
    }

    private static String stripTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
