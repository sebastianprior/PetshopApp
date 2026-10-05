package com.petshop.app.payment.soap;

import com.petshop.app.payment.AuthResult;
import com.petshop.app.payment.GatewayFaultException;
import com.petshop.app.payment.PasarelaBancariaSimulada;
import com.petshop.app.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagosSoapTest {

    private final PagosEndpoint endpoint = new PagosEndpoint(new PasarelaBancariaSimulada(0));

    @Test
    void endpointApprovesRejectsAndRemembersTransactions() {
        assertThat(endpoint.autorizar(new AutorizarPagoRequest("T1", 100, "tok_aprobado")).estado).isEqualTo("APROBADO");

        AutorizarPagoResponse rejected = endpoint.autorizar(new AutorizarPagoRequest("T2", 100, "tok_rechazado"));
        assertThat(rejected.estado).isEqualTo("RECHAZADO");
        assertThat(rejected.motivo).isEqualTo("Fondos insuficientes");

        ConsultarPagoResponse known = endpoint.consultar(new ConsultarPagoRequest("T1"));
        assertThat(known.encontrada).isTrue();
        assertThat(known.estado).isEqualTo("APROBADO");
        assertThat(endpoint.consultar(new ConsultarPagoRequest("nunca")).encontrada).isFalse();
    }

    @Test
    void endpointIsIdempotentPerTransaction() {
        endpoint.autorizar(new AutorizarPagoRequest("T3", 100, "tok_rechazado"));

        assertThat(endpoint.autorizar(new AutorizarPagoRequest("T3", 100, "tok_aprobado")).estado).isEqualTo("RECHAZADO");
    }

    @Test
    void invalidDataBecomesAClientFaultAndOthersAServerFault() {
        GatewayFaultResolver resolver = new GatewayFaultResolver();
        MessageContext context = mock(MessageContext.class);
        SoapMessage response = mock(SoapMessage.class);
        SoapBody body = mock(SoapBody.class);
        when(context.getResponse()).thenReturn(response);
        when(response.getSoapBody()).thenReturn(body);

        boolean handled = resolver.resolveException(context, endpoint,
                new GatewayFaultException(GatewayFaultException.INVALID_DATA, "Datos de pago inválidos"));
        resolver.resolveException(context, endpoint, new GatewayFaultException(GatewayFaultException.UNAVAILABLE, "caída"));

        assertThat(handled).isTrue();
        verify(body).addClientOrSenderFault("PG-400: Datos de pago inválidos", Locale.ROOT);
        verify(body).addServerOrReceiverFault("PG-503: caída", Locale.ROOT);
        assertThat(resolver.resolveException(context, endpoint, new IllegalStateException("otro"))).isFalse();
    }

    @Test
    void clientMapsResponsesToAuthResults() {
        WebServiceTemplate template = mock(WebServiceTemplate.class);
        SoapPaymentGateway gateway = new SoapPaymentGateway(template);
        when(template.marshalSendAndReceive(any(AutorizarPagoRequest.class)))
                .thenReturn(new AutorizarPagoResponse("RECHAZADO", "Fondos insuficientes"));
        when(template.marshalSendAndReceive(any(ConsultarPagoRequest.class)))
                .thenReturn(new ConsultarPagoResponse(true, "APROBADO", null))
                .thenReturn(new ConsultarPagoResponse(false, null, null));

        assertThat(gateway.autorizar("T1", 10, "tok")).isEqualTo(new AuthResult(PaymentStatus.RECHAZADO, "Fondos insuficientes"));
        assertThat(gateway.consultarPago("T1")).contains(new AuthResult(PaymentStatus.APROBADO, null));
        assertThat(gateway.consultarPago("T2")).isEqualTo(Optional.empty());
    }

    @Test
    void clientTranslatesSoapFaultsAndIoErrors() {
        WebServiceTemplate template = mock(WebServiceTemplate.class);
        SoapPaymentGateway gateway = new SoapPaymentGateway(template);

        SoapFaultClientException invalid = mock(SoapFaultClientException.class);
        when(invalid.getFaultStringOrReason()).thenReturn("PG-400: Datos de pago inválidos");
        when(template.marshalSendAndReceive(any(AutorizarPagoRequest.class))).thenThrow(invalid);
        assertThatThrownBy(() -> gateway.autorizar("T1", 10, "tok"))
                .isInstanceOfSatisfying(GatewayFaultException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(GatewayFaultException.INVALID_DATA);
                    assertThat(e.getMessage()).isEqualTo("Datos de pago inválidos");
                });

        SoapFaultClientException down = mock(SoapFaultClientException.class);
        when(down.getFaultStringOrReason()).thenReturn("PG-503: Pasarela no disponible");
        when(template.marshalSendAndReceive(any(AutorizarPagoRequest.class))).thenThrow(down);
        assertThatThrownBy(() -> gateway.autorizar("T1", 10, "tok"))
                .isInstanceOfSatisfying(GatewayFaultException.class,
                        e -> assertThat(e.getCode()).isEqualTo(GatewayFaultException.UNAVAILABLE));

        when(template.marshalSendAndReceive(any(AutorizarPagoRequest.class)))
                .thenThrow(new org.springframework.ws.client.WebServiceIOException("connection refused"));
        assertThatThrownBy(() -> gateway.autorizar("T1", 10, "tok"))
                .isInstanceOfSatisfying(GatewayFaultException.class,
                        e -> assertThat(e.getCode()).isEqualTo(GatewayFaultException.UNAVAILABLE));
    }
}
