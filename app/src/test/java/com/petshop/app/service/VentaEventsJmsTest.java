package com.petshop.app.service;

import com.petshop.app.model.OutboxEvent;
import com.petshop.app.model.ProcessedEvent;
import com.petshop.app.model.User;
import com.petshop.app.repository.OutboxEventRepository;
import com.petshop.app.repository.ProcessedEventRepository;
import com.petshop.app.repository.UserRepository;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mensajería de ventas: el evento se guarda en la tabla outbox, el relay lo entrega al broker con reintentos y el
 * consumidor lo procesa una sola vez.
 */
class VentaEventsJmsTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private OutboxEventRepository outbox;
    private JmsTemplate jms;
    private List<OutboxEvent> table;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxEventRepository.class);
        jms = mock(JmsTemplate.class);
        table = new ArrayList<>();
        when(outbox.save(any(OutboxEvent.class))).thenAnswer(inv -> {
            OutboxEvent saved = inv.getArgument(0);
            if (saved.id == null) {
                saved.id = (long) table.size() + 1;
                table.add(saved);
            }
            return saved;
        });
        when(outbox.findTop50BySentAtIsNullOrderByIdAsc()).thenAnswer(inv -> table.stream().filter(e -> e.sentAt == null).toList());
    }

    // ---- Publicación: el evento queda guardado antes de ir al broker -------------------------------------------------

    @Test
    void eachEventIsStoredForItsOwnQueueAsJsonWithAUniqueId() {
        VentaEventPublisher publisher = new VentaEventPublisher(outbox, json);

        publisher.onVentaConfirmada(new VentaEvents.VentaConfirmada(5L, "u1", 3500.0));
        publisher.onVentaCancelada(new VentaEvents.VentaCancelada(6L, "u1", "sin stock"));
        publisher.onPagoFallido(new VentaEvents.PagoFallido(7L, "u1", "tx-1", 3));

        assertThat(table).extracting(e -> e.queue).containsExactly("ventas.confirmadas", "ventas.canceladas", "pagos.fallidos");
        assertThat(table.get(0).payload).contains("\"ventaId\":5");
        assertThat(table.get(1).payload).contains("\"motivo\":\"sin stock\"");
        assertThat(table.get(2).payload).contains("\"idTransaccion\":\"tx-1\"");
        assertThat(table).extracting(e -> e.eventId).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).hasSize(36));
        assertThat(table).allSatisfy(e -> {
            assertThat(e.sentAt).isNull();
            assertThat(e.createdAt).isNotNull();
        });
        verify(jms, never()).send(anyString(), any(MessageCreator.class));
    }

    @Test
    void ifTheEventCannotBeStoredTheSaleIsNotAffected() {
        when(outbox.save(any(OutboxEvent.class))).thenThrow(new IllegalStateException("base caída"));

        new VentaEventPublisher(outbox, json).onVentaConfirmada(new VentaEvents.VentaConfirmada(5L, "u1", 10.0));
    }

    // ---- Entrega al broker ----------------------------------------------------------------------------------------------

    private OutboxEvent pending(String queue, String eventId) {
        OutboxEvent event = new OutboxEvent(eventId, queue, "{\"ventaId\":1}", Instant.now());
        outbox.save(event);
        return event;
    }

    @Test
    void theRelayDeliversPendingEventsInOrderAndMarksThemSent() throws Exception {
        OutboxEvent first = pending("ventas.confirmadas", "e-1");
        OutboxEvent second = pending("ventas.canceladas", "e-2");

        int delivered = new OutboxRelay(outbox, jms).entregarPendientes();

        assertThat(delivered).isEqualTo(2);
        assertThat(first.sentAt).isNotNull();
        assertThat(second.sentAt).isNotNull();
        assertThat(first.attempts).isEqualTo(1);
        assertThat(first.lastError).isNull();
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(jms);
        ArgumentCaptor<MessageCreator> creator = ArgumentCaptor.forClass(MessageCreator.class);
        order.verify(jms).send(eq("ventas.confirmadas"), creator.capture());
        order.verify(jms).send(eq("ventas.canceladas"), any(MessageCreator.class));

        Session session = mock(Session.class);
        TextMessage message = mock(TextMessage.class);
        when(session.createTextMessage("{\"ventaId\":1}")).thenReturn(message);
        creator.getValue().createMessage(session);
        verify(message).setStringProperty("eventId", "e-1");
    }

    @Test
    void whenTheBrokerIsDownTheEventStaysPendingAndTheRoundStopsToKeepTheOrder() {
        OutboxEvent first = pending("ventas.confirmadas", "e-1");
        OutboxEvent second = pending("ventas.canceladas", "e-2");
        doThrow(new org.springframework.jms.UncategorizedJmsException("broker caído")).when(jms).send(anyString(), any(MessageCreator.class));

        int delivered = new OutboxRelay(outbox, jms).entregarPendientes();

        assertThat(delivered).isZero();
        assertThat(first.sentAt).isNull();
        assertThat(first.attempts).isEqualTo(1);
        assertThat(first.lastError).contains("broker caído");
        assertThat(second.attempts).as("no se intentó el segundo para no saltear el orden").isZero();
        verify(jms, times(1)).send(anyString(), any(MessageCreator.class));
    }

    @Test
    void whenTheBrokerComesBackNothingWasLostAndTheOrderIsKept() {
        OutboxEvent first = pending("ventas.confirmadas", "e-1");
        OutboxEvent second = pending("ventas.canceladas", "e-2");
        OutboxRelay relay = new OutboxRelay(outbox, jms);
        doThrow(new org.springframework.jms.UncategorizedJmsException("broker caído")).when(jms).send(anyString(), any(MessageCreator.class));
        assertThat(relay.entregarPendientes()).isZero();

        org.mockito.Mockito.reset(jms); // el broker volvió
        int delivered = relay.entregarPendientes();

        assertThat(delivered).isEqualTo(2);
        assertThat(first.sentAt).isNotNull();
        assertThat(second.sentAt).isNotNull();
        assertThat(first.attempts).isEqualTo(2);
        assertThat(first.lastError).as("el error se limpia al entregar").isNull();
        assertThat(relay.entregarPendientes()).as("lo ya entregado no se manda de nuevo").isZero();
    }

    @Test
    void anOverlongErrorMessageIsTrimmedToFitTheColumn() {
        OutboxEvent event = pending("ventas.confirmadas", "e-1");
        doThrow(new org.springframework.jms.UncategorizedJmsException("x".repeat(2000))).when(jms).send(anyString(), any(MessageCreator.class));

        new OutboxRelay(outbox, jms).entregarPendientes();

        assertThat(event.lastError).hasSizeLessThanOrEqualTo(500);
    }

    @Test
    void theScheduledRunDeliversToo() {
        OutboxEvent event = pending("ventas.confirmadas", "e-1");

        new OutboxRelay(outbox, jms).programado();

        assertThat(event.sentAt).isNotNull();
    }

    // ---- Consumo: una sola vez ----------------------------------------------------------------------------------------

    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);

    private VentaEventListener listener() {
        User user = new User();
        user.email = "cliente@example.com";
        when(users.findById("u1")).thenReturn(Optional.of(user));
        return new VentaEventListener(users, notifications, json, processed);
    }

    @Test
    void theConsumerNotifiesTheCustomerFromTheQueueMessage() {
        VentaEventListener listener = listener();

        listener.onVentaConfirmada("{\"ventaId\":5,\"userId\":\"u1\",\"total\":3500.0}", "e-5");
        listener.onVentaCancelada("{\"ventaId\":6,\"userId\":\"u1\",\"motivo\":\"sin stock\"}", "e-6");
        listener.onPagoFallido("{\"ventaId\":7,\"userId\":\"u1\",\"idTransaccion\":\"tx-1\",\"intentos\":3}", "e-7");

        verify(notifications).notify(eq("cliente@example.com"), contains("#5 fue confirmada"));
        verify(notifications).notify(eq("cliente@example.com"), contains("#6 fue cancelada: sin stock"));
        verify(notifications, never()).notify(anyString(), contains("#7"));
        ArgumentCaptor<ProcessedEvent> saved = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processed, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(p -> p.eventId).containsExactly("e-5", "e-6", "e-7");
        assertThat(saved.getAllValues()).extracting(p -> p.queue).containsExactly("ventas.confirmadas", "ventas.canceladas", "pagos.fallidos");
    }

    @Test
    void aDuplicateMessageIsProcessedOnlyOnce() {
        VentaEventListener listener = listener();
        when(processed.existsById("e-5")).thenReturn(false, true);
        String message = "{\"ventaId\":5,\"userId\":\"u1\",\"total\":3500.0}";

        listener.onVentaConfirmada(message, "e-5");
        listener.onVentaConfirmada(message, "e-5");
        listener.onVentaConfirmada(message, "e-5");

        verify(notifications, times(1)).notify(anyString(), anyString());
        verify(processed, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    void aFailureIsNotMarkedAsProcessedSoTheBrokerCanRetryIt() {
        VentaEventListener listener = listener();
        doThrow(new IllegalStateException("correo caído")).when(notifications).notify(anyString(), anyString());

        assertThatThrownBy(() -> listener.onVentaConfirmada("{\"ventaId\":5,\"userId\":\"u1\",\"total\":1.0}", "e-5"))
                .isInstanceOf(IllegalStateException.class);

        verify(processed, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void anInvalidMessageFailsSoItEndsUpInTheDeadLetterQueue() {
        VentaEventListener listener = listener();

        assertThatThrownBy(() -> listener.onVentaConfirmada("esto no es json", "e-9")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> listener.onVentaCancelada("{\"ventaId\":", "e-10")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> listener.onPagoFallido("[]", "e-11")).isInstanceOf(RuntimeException.class);

        verify(processed, never()).save(any(ProcessedEvent.class));
        verify(notifications, never()).notify(anyString(), anyString());
    }

    @Test
    void aMessageWithoutEventIdIsStillProcessedButNotRegistered() {
        VentaEventListener listener = listener();

        listener.onVentaConfirmada("{\"ventaId\":5,\"userId\":\"u1\",\"total\":3500.0}", null);

        verify(notifications).notify(eq("cliente@example.com"), contains("#5 fue confirmada"));
        verify(processed, never()).existsById(anyString());
        verify(processed, never()).save(any(ProcessedEvent.class));
    }
}
