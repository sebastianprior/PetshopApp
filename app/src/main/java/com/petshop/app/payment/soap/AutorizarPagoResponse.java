package com.petshop.app.payment.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "AutorizarPagoResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class AutorizarPagoResponse {
    /** APROBADO o RECHAZADO. */
    public String estado;
    public String motivo;

    public AutorizarPagoResponse() {
    }

    public AutorizarPagoResponse(String estado, String motivo) {
        this.estado = estado;
        this.motivo = motivo;
    }
}
