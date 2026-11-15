package com.petshop.app.payment;

public record AuthResult(PaymentStatus estado, String motivo) {}
