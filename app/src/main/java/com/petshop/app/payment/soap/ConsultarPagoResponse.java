package com.petshop.app.payment.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ConsultarPagoResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConsultarPagoResponse {
    /** false si la pasarela nunca registró esa transacción. */
    public boolean encontrada;
    public String estado;
    public String motivo;

    public ConsultarPagoResponse() {
    }

    public ConsultarPagoResponse(boolean encontrada, String estado, String motivo) {
        this.encontrada = encontrada;
        this.estado = estado;
        this.motivo = motivo;
    }
}
