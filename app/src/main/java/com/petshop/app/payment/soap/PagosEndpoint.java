package com.petshop.app.payment.soap;

import com.petshop.app.payment.AuthResult;
import com.petshop.app.payment.PasarelaBancariaSimulada;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/** Servicio SOAP de la pasarela bancaria (contrato en ws/pagos.wsdl, publicado en /ws/pagos.wsdl). */
@Endpoint
public class PagosEndpoint {

    public static final String NAMESPACE = "urn:petshop:pagos";

    private final PasarelaBancariaSimulada pasarela;

    public PagosEndpoint(PasarelaBancariaSimulada pasarela) {
        this.pasarela = pasarela;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "AutorizarPagoRequest")
    @ResponsePayload
    public AutorizarPagoResponse autorizar(@RequestPayload AutorizarPagoRequest request) {
        AuthResult result = pasarela.autorizar(request.idTransaccion, request.monto, request.medioPagoToken);
        return new AutorizarPagoResponse(result.estado().name(), result.motivo());
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "ConsultarPagoRequest")
    @ResponsePayload
    public ConsultarPagoResponse consultar(@RequestPayload ConsultarPagoRequest request) {
        return pasarela.consultarPago(request.idTransaccion)
                .map(r -> new ConsultarPagoResponse(true, r.estado().name(), r.motivo()))
                .orElseGet(() -> new ConsultarPagoResponse(false, null, null));
    }
}
