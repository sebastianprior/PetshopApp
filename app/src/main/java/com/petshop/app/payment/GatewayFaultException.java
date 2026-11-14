package com.petshop.app.payment;

public class GatewayFaultException extends RuntimeException {

    public static final String INVALID_DATA = "PG-400";
    public static final String UNAVAILABLE = "PG-503";

    private final String code;

    public GatewayFaultException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
