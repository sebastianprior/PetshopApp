package com.petshop.app.payment.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "AutorizarPagoRequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class AutorizarPagoRequest {
    public String idTransaccion;
    public double monto;
    public String medioPagoToken;

    public AutorizarPagoRequest() {
    }

    public AutorizarPagoRequest(String idTransaccion, double monto, String medioPagoToken) {
        this.idTransaccion = idTransaccion;
        this.monto = monto;
        this.medioPagoToken = medioPagoToken;
    }
}
