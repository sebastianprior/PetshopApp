package com.petshop.app.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class VentaRequest {
    public String nombre;
    public String direccion;
    public String ciudad;
    public String codigoPostal;
    public String telefono;
    public String cupon;
    public String medioPago;

    public String hash() {
        String canonical = String.join("\u0001", List.of(
                norm(nombre), norm(direccion), norm(ciudad), norm(codigoPostal),
                norm(telefono), norm(cupon).toUpperCase(), norm(medioPago)));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim();
    }
}
