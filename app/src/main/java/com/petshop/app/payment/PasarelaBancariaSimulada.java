package com.petshop.app.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lógica de la pasarela bancaria legada, expuesta por SOAP en /ws (ver soap/PagosEndpoint).
 * Es idempotente por idTransaccion:
 * reautorizar la misma transacción devuelve el resultado ya registrado, sin cobrar dos veces.
 *
 * Tokens de prueba: tok_rechazado (fondos insuficientes), tok_invalido (fault PG-400),
 * tok_demora (aprueba pero responde tarde), tok_caido (no responde), cualquier otro aprueba.
 */
@Component
public class PasarelaBancariaSimulada {

    private final ConcurrentHashMap<String, AuthResult> transacciones = new ConcurrentHashMap<>();
    private final long simulatedDelayMs;

    public PasarelaBancariaSimulada(@Value("${petshop.payments.simulated-delay-ms:6000}") long simulatedDelayMs) {
        this.simulatedDelayMs = simulatedDelayMs;
    }

    public AuthResult autorizar(String idTransaccion, double monto, String medioPagoToken) {
        if ("tok_invalido".equals(medioPagoToken)) {
            throw new GatewayFaultException(GatewayFaultException.INVALID_DATA, "Datos de pago inválidos");
        }
        if ("tok_caido".equals(medioPagoToken)) {
            demorar();
            throw new GatewayFaultException(GatewayFaultException.UNAVAILABLE, "Pasarela no disponible");
        }

        AuthResult result = transacciones.computeIfAbsent(idTransaccion, id ->
                "tok_rechazado".equals(medioPagoToken)
                        ? new AuthResult(PaymentStatus.RECHAZADO, "Fondos insuficientes")
                        : new AuthResult(PaymentStatus.APROBADO, null));

        if ("tok_demora".equals(medioPagoToken)) {
            demorar();
        }
        return result;
    }

    public Optional<AuthResult> consultarPago(String idTransaccion) {
        return Optional.ofNullable(transacciones.get(idTransaccion));
    }

    private void demorar() {
        try {
            Thread.sleep(simulatedDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayFaultException(GatewayFaultException.UNAVAILABLE, "Pasarela interrumpida");
        }
    }
}
