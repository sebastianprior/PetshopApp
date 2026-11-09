package com.petshop.app.controller;

import com.petshop.app.payment.mercadopago.MercadoPagoClient;
import com.petshop.app.payment.mercadopago.MercadoPagoClient.MercadoPagoException;
import com.petshop.app.payment.mercadopago.MercadoPagoSettings;
import com.petshop.app.service.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentsControllerTest {

    private MercadoPagoClient mercadoPago;
    private VentaService ventaService;
    private PaymentsController controller;

    @BeforeEach
    void setUp() {
        mercadoPago = mock(MercadoPagoClient.class);
        ventaService = mock(VentaService.class);
        when(mercadoPago.isEnabled()).thenReturn(true);
        controller = controller(true);
    }

    private PaymentsController controller(boolean simulated) {
        return new PaymentsController(mercadoPago, new MercadoPagoSettings("TEST-x", "https://api.mercadopago.com", 30, "http://localhost:5173", "", simulated), ventaService);
    }

    @Test
    void tellsWhichPaymentMethodsAreAvailableWithoutExposingAnyCredential() {
        Map<String, Object> config = controller.config();

        assertThat(config).containsOnlyKeys("mercadoPago", "simulated");
        assertThat(config.get("mercadoPago")).isEqualTo(true);
        assertThat(config.get("simulated")).isEqualTo(true);
        assertThat(config.toString()).doesNotContain("TEST-x");

        when(mercadoPago.isEnabled()).thenReturn(false);
        assertThat(controller(false).config()).containsEntry("mercadoPago", false).containsEntry("simulated", false);
    }

    @Test
    void aPaymentNoticeInTheQueryStringIsProcessedByItsPaymentId() throws Exception {
        ResponseEntity<?> response = controller.webhook(Map.of("type", "payment", "data.id", "12345"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(ventaService).procesarAvisoMercadoPago("12345");
    }

    @Test
    void aPaymentNoticeInTheBodyIsProcessedToo() throws Exception {
        ResponseEntity<?> response = controller.webhook(Map.of(), Map.of("type", "payment", "data", Map.of("id", 777)));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(ventaService).procesarAvisoMercadoPago("777");
    }

    @Test
    void theOlderTopicFormatIsAccepted() throws Exception {
        controller.webhook(Map.of("topic", "payment", "id", "42"), null);

        verify(ventaService).procesarAvisoMercadoPago("42");
    }

    @Test
    void noticesThatAreNotAboutPaymentsAreAcknowledgedAndIgnored() throws Exception {
        assertThat(controller.webhook(Map.of("type", "merchant_order", "data.id", "1"), null).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.webhook(Map.of("topic", "merchant_order", "id", "1"), null).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.webhook(Map.of(), null).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.webhook(Map.of("type", "payment"), null).getStatusCode().value()).isEqualTo(200); // sin id de pago

        verify(ventaService, never()).procesarAvisoMercadoPago(anyString());
    }

    @Test
    void ifMercadoPagoCannotBeQueriedItAnswersWithAnErrorSoItRetriesLater() throws Exception {
        doThrow(new MercadoPagoException("sin conexión")).when(ventaService).procesarAvisoMercadoPago("9");

        ResponseEntity<?> response = controller.webhook(Map.of("type", "payment", "data.id", "9"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
    }

    @Test
    void theWebhookDoesNotExistWhenMercadoPagoIsNotConfigured() throws Exception {
        when(mercadoPago.isEnabled()).thenReturn(false);

        ResponseEntity<?> response = controller.webhook(Map.of("type", "payment", "data.id", "1"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(ventaService, never()).procesarAvisoMercadoPago(anyString());
    }
}
