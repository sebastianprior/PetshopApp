package com.petshop.app.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La pasarela simulada responde según el token de prueba y es idempotente por idTransaccion. */
class PasarelaBancariaSimuladaTest {

    private final PasarelaBancariaSimulada pasarela = new PasarelaBancariaSimulada(40);

    @Test
    void approvesAnyOrdinaryToken() {
        AuthResult result = pasarela.autorizar("tx-1", 100.0, "tok_aprobado");

        assertThat(result.estado()).isEqualTo(PaymentStatus.APROBADO);
        assertThat(result.motivo()).isNull();
    }

    @Test
    void rejectsWithInsufficientFundsAndExplainsWhy() {
        AuthResult result = pasarela.autorizar("tx-2", 100.0, "tok_rechazado");

        assertThat(result.estado()).isEqualTo(PaymentStatus.RECHAZADO);
        assertThat(result.motivo()).isEqualTo("Fondos insuficientes");
    }

    @Test
    void authorizingTheSameTransactionTwiceReturnsTheFirstResultWithoutChargingAgain() {
        AuthResult first = pasarela.autorizar("tx-3", 100.0, "tok_rechazado");
        AuthResult again = pasarela.autorizar("tx-3", 100.0, "tok_aprobado");

        assertThat(again).isEqualTo(first);
        assertThat(again.estado()).isEqualTo(PaymentStatus.RECHAZADO);
    }

    @Test
    void anInvalidTokenIsATechnicalFaultAndLeavesNoTransaction() {
        assertThatThrownBy(() -> pasarela.autorizar("tx-4", 100.0, "tok_invalido"))
                .isInstanceOfSatisfying(GatewayFaultException.class,
                        e -> assertThat(e.getCode()).isEqualTo(GatewayFaultException.INVALID_DATA));

        assertThat(pasarela.consultarPago("tx-4")).isEmpty();
    }

    @Test
    void aDownGatewayFailsAfterTheSimulatedDelayAndLeavesNoTransaction() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> pasarela.autorizar("tx-5", 100.0, "tok_caido"))
                .isInstanceOfSatisfying(GatewayFaultException.class,
                        e -> assertThat(e.getCode()).isEqualTo(GatewayFaultException.UNAVAILABLE));

        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(35);
        assertThat(pasarela.consultarPago("tx-5")).isEmpty();
    }

    @Test
    void aSlowGatewayStillApprovesButTakesTheSimulatedDelay() {
        long start = System.nanoTime();

        AuthResult result = pasarela.autorizar("tx-6", 100.0, "tok_demora");

        assertThat(result.estado()).isEqualTo(PaymentStatus.APROBADO);
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(35);
    }

    @Test
    void anInterruptedWaitReportsTheGatewayAsUnavailableAndKeepsTheInterruption() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> pasarela.autorizar("tx-7", 100.0, "tok_caido"))
                    .isInstanceOfSatisfying(GatewayFaultException.class,
                            e -> assertThat(e.getMessage()).isEqualTo("Pasarela interrumpida"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // no dejar la marca puesta para otros tests
        }
    }

    @Test
    void queryingAnApprovedTransactionReturnsItAndAnUnknownOneIsEmpty() {
        pasarela.autorizar("tx-8", 50.0, "tok_aprobado");

        assertThat(pasarela.consultarPago("tx-8")).contains(new AuthResult(PaymentStatus.APROBADO, null));
        assertThat(pasarela.consultarPago("no-existe")).isEmpty();
    }
}
