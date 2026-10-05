package com.petshop.app.payment;

import java.util.Optional;

public interface PaymentGateway {

    AuthResult autorizar(String idTransaccion, double monto, String medioPagoToken);

    Optional<AuthResult> consultarPago(String idTransaccion);
}
