package com.petshop.app.payment.soap;

import com.petshop.app.payment.GatewayFaultException;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.AbstractEndpointExceptionResolver;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapMessage;

import java.util.Locale;

/** Traduce los errores de la pasarela a SOAP Faults: PG-400 es del cliente (Sender), el resto del servidor (Receiver). */
public class GatewayFaultResolver extends AbstractEndpointExceptionResolver {

    @Override
    protected boolean resolveExceptionInternal(MessageContext messageContext, Object endpoint, Exception ex) {
        if (!(ex instanceof GatewayFaultException fault)) {
            return false;
        }
        SoapBody body = ((SoapMessage) messageContext.getResponse()).getSoapBody();
        String reason = fault.getCode() + ": " + fault.getMessage();
        if (GatewayFaultException.INVALID_DATA.equals(fault.getCode())) {
            body.addClientOrSenderFault(reason, Locale.ROOT);
        } else {
            body.addServerOrReceiverFault(reason, Locale.ROOT);
        }
        return true;
    }
}
