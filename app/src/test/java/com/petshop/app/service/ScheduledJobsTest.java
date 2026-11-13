package com.petshop.app.service;

import com.petshop.app.model.ResetToken;
import com.petshop.app.repository.ResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Procesos que corren solos cada cierto tiempo: limpiar tokens vencidos y revisar los pagos pendientes. */
class ScheduledJobsTest {

    private static ResetToken token(String value, long offsetMs) {
        return new ResetToken(value, value + "@ejemplo.com", System.currentTimeMillis() + offsetMs);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetTokenCleanerRemovesOnlyTheExpiredTokens() {
        ResetTokenRepository repository = mock(ResetTokenRepository.class);
        ResetToken expired = token("vencido", -60_000);
        ResetToken alsoExpired = token("vencido2", -1);
        ResetToken valid = token("vigente", 60_000);
        when(repository.findAll()).thenReturn(List.of(expired, valid, alsoExpired));

        new ResetTokenCleaner(repository).clean();

        ArgumentCaptor<List<ResetToken>> removed = ArgumentCaptor.forClass(List.class);
        verify(repository).deleteAll(removed.capture());
        assertThat(removed.getValue()).containsExactlyInAnyOrder(expired, alsoExpired);
        assertThat(removed.getValue()).doesNotContain(valid);
    }

    @Test
    void resetTokenCleanerDeletesNothingWhenAllTokensAreStillValid() {
        ResetTokenRepository repository = mock(ResetTokenRepository.class);
        when(repository.findAll()).thenReturn(List.of(token("a", 60_000), token("b", 3_600_000)));

        new ResetTokenCleaner(repository).clean();

        verify(repository, never()).deleteAll(anyList());
    }

    @Test
    void resetTokenCleanerHandlesAnEmptyTable() {
        ResetTokenRepository repository = mock(ResetTokenRepository.class);
        when(repository.findAll()).thenReturn(List.of());

        new ResetTokenCleaner(repository).clean();

        verify(repository, never()).deleteAll(anyList());
    }

    @Test
    void pendingPaymentWorkerAsksTheSalesServiceToProcessThePendingOrders() {
        VentaService ventaService = mock(VentaService.class);

        new PendingPaymentWorker(ventaService).procesar();

        verify(ventaService).procesarPendientes();
    }
}
