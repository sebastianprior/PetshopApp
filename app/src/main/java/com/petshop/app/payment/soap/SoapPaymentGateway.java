package com.petshop.app.payment.soap;

import com.petshop.app.payment.AuthResult;
import com.petshop.app.payment.GatewayFaultException;
import com.petshop.app.payment.PaymentGateway;
import com.petshop.app.payment.PaymentStatus;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.util.Optional;

/** Cliente SOAP de la pasarela: arma el sobre XML y lo envía por HTTP a {@code petshop.payments.soap-url}. */
@Component
public class SoapPaymentGateway implements PaymentGateway {

    private final WebServiceTemplate template;

    public SoapPaymentGateway(WebServiceTemplate pagosWebServiceTemplate) {
        this.template = pagosWebServiceTemplate;
    }

    @Override
    public AuthResult autorizar(String idTransaccion, double monto, String medioPagoToken) {
        AutorizarPagoResponse response = enviar(new AutorizarPagoRequest(idTransaccion, monto, medioPagoToken));
        return new AuthResult(PaymentStatus.valueOf(response.estado), response.motivo);
    }

    @Override
    public Optional<AuthResult> consultarPago(String idTransaccion) {
        ConsultarPagoResponse response = enviar(new ConsultarPagoRequest(idTransaccion));
        return response.encontrada
                ? Optional.of(new AuthResult(PaymentStatus.valueOf(response.estado), response.motivo))
                : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private <T> T enviar(Object request) {
        try {
            return (T) template.marshalSendAndReceive(request);
        } catch (SoapFaultClientException e) {
            String reason = e.getFaultStringOrReason() != null ? e.getFaultStringOrReason() : "";
            String code = reason.startsWith(GatewayFaultException.INVALID_DATA)
                    ? GatewayFaultException.INVALID_DATA : GatewayFaultException.UNAVAILABLE;
            throw new GatewayFaultException(code, reason.isBlank() ? "SOAP Fault" : reason.replaceFirst("^PG-\\d+: ", ""));
        } catch (WebServiceIOException e) {
            throw new GatewayFaultException(GatewayFaultException.UNAVAILABLE, "Pasarela inaccesible: " + e.getMessage());
        }
    }
}
