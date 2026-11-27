package com.petshop.app.service;

import com.petshop.app.dto.VentaRequest;

import java.util.regex.Pattern;

/**
 * Valida los datos de envío y el cupón de una venta antes de tocar stock o pagos. El navegador ya los revisa, pero la API
 * no puede confiar en eso: cualquiera puede llamarla sin pasar por el formulario. Las reglas son más flexibles que las del
 * formulario (que además pide largos mínimos) para no rechazar clientes de la API que ya funcionaban con datos cortos.
 */
public final class DatosEnvioValidator {

    static final int MAX_NOMBRE = 80;
    static final int MAX_DIRECCION = 120;
    static final int MAX_CIUDAD = 60;
    static final int MAX_CUPON = 30;

    /** Letras (con tildes), espacios, puntos, apóstrofes y guiones: "María José", "D'Angelo", "Pérez-Gómez". */
    private static final Pattern NOMBRE = Pattern.compile("^\\p{L}[\\p{L} .'’-]*$");
    /** Como el nombre, más números y los signos habituales de una dirección: "Av. Corrientes 1234, 5° B". */
    private static final Pattern DIRECCION = Pattern.compile("^[\\p{L}\\d][\\p{L}\\d .,'’#°ºª/-]*$");
    private static final Pattern TELEFONO = Pattern.compile("^[\\d ()+-]+$");
    private static final Pattern CUPON = Pattern.compile("^[A-Za-z0-9_-]+$");

    private DatosEnvioValidator() {
    }

    /** Devuelve el motivo del rechazo (listo para mostrarle al cliente), o {@code null} si los datos son válidos. */
    public static String error(VentaRequest req) {
        String nombre = trim(req.nombre);
        if (nombre.length() > MAX_NOMBRE || !NOMBRE.matcher(nombre).matches()) {
            return "El nombre solo puede tener letras, espacios, puntos, apóstrofes y guiones (hasta " + MAX_NOMBRE + " caracteres).";
        }
        String direccion = trim(req.direccion);
        if (direccion.length() > MAX_DIRECCION || !DIRECCION.matcher(direccion).matches()) {
            return "La dirección tiene caracteres no permitidos o es demasiado larga (hasta " + MAX_DIRECCION + " caracteres).";
        }
        String ciudad = trim(req.ciudad);
        if (ciudad.length() > MAX_CIUDAD || !NOMBRE.matcher(ciudad).matches()) {
            return "La ciudad solo puede tener letras, espacios, puntos, apóstrofes y guiones (hasta " + MAX_CIUDAD + " caracteres).";
        }
        String telefono = trim(req.telefono);
        if (!telefono.isEmpty() && !telefonoValido(telefono)) {
            return "El teléfono debe tener entre 6 y 15 números (se permiten espacios, guiones, paréntesis y el +).";
        }
        String cupon = trim(req.cupon);
        if (!cupon.isEmpty() && (cupon.length() > MAX_CUPON || !CUPON.matcher(cupon).matches())) {
            return "El código de cupón solo puede tener letras, números, guiones y guiones bajos (hasta " + MAX_CUPON + " caracteres).";
        }
        return null;
    }

    private static boolean telefonoValido(String telefono) {
        if (telefono.length() > 25 || !TELEFONO.matcher(telefono).matches()) {
            return false;
        }
        long digitos = telefono.chars().filter(Character::isDigit).count();
        return digitos >= 6 && digitos <= 15;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
