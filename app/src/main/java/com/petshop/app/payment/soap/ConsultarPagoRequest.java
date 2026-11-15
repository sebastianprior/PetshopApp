package com.petshop.app.payment.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ConsultarPagoRequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConsultarPagoRequest {
    public String idTransaccion;

    public ConsultarPagoRequest() {
    }

    public ConsultarPagoRequest(String idTransaccion) {
        this.idTransaccion = idTransaccion;
    }
}
