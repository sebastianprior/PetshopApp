package com.petshop.app.service;

import com.petshop.app.model.User;
import com.petshop.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VentaEventsJmsTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final JmsTemplate jms = mock(JmsTemplate.class);
    private final VentaEventPublisher publisher = new VentaEventPublisher(jms, json);

    @Test
    void eachEventGoesToItsOwnQueueAsJson() {
        publisher.onVentaConfirmada(new VentaEvents.VentaConfirmada(5L, "u1", 3500.0));
        publisher.onVentaCancelada(new VentaEvents.VentaCancelada(6L, "u1", "sin stock"));
        publisher.onPagoFallido(new VentaEvents.PagoFallido(7L, "u1", "tx-1", 3));

        verify(jms).convertAndSend(eq("ventas.confirmadas"), contains("\"ventaId\":5"));
        verify(jms).convertAndSend(eq("ventas.canceladas"), contains("\"motivo\":\"sin stock\""));
        verify(jms).convertAndSend(eq("pagos.fallidos"), contains("\"idTransaccion\":\"tx-1\""));
    }

    @Test
    void aBrokerFailureNeverBreaksTheSale() {
        doThrow(new org.springframework.jms.UncategorizedJmsException("broker caído"))
                .when(jms).convertAndSend(anyString(), anyString());

        publisher.onVentaConfirmada(new VentaEvents.VentaConfirmada(5L, "u1", 10.0));
    }

    @Test
    void theConsumerNotifiesTheCustomerFromTheQueueMessage() {
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        User user = new User();
        user.email = "cliente@example.com";
        when(users.findById("u1")).thenReturn(Optional.of(user));
        VentaEventListener listener = new VentaEventListener(users, notifications, json);

        listener.onVentaConfirmada("{\"ventaId\":5,\"userId\":\"u1\",\"total\":3500.0}");
        listener.onVentaCancelada("{\"ventaId\":6,\"userId\":\"u1\",\"motivo\":\"sin stock\"}");
        listener.onPagoFallido("{\"ventaId\":7,\"userId\":\"u1\",\"idTransaccion\":\"tx-1\",\"intentos\":3}");

        verify(notifications).notify(eq("cliente@example.com"), contains("#5 fue confirmada"));
        verify(notifications).notify(eq("cliente@example.com"), contains("#6 fue cancelada: sin stock"));
        verify(notifications, never()).notify(anyString(), contains("#7"));
    }
}
