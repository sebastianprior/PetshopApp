package com.petshop.app.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * ETag de un recurso: una huella de los datos que pueden cambiar. Quien lee el recurso recibe su ETag y, al actualizarlo,
 * lo devuelve en el header If-Match: si mientras tanto otra persona lo modificó, la huella ya no coincide y la
 * actualización se rechaza con 412 (Precondition Failed) en lugar de pisar el cambio ajeno (RFC 9110, sección 13.1.1).
 */
public final class ETags {

    private ETags() {}

    /** Huella entre comillas (formato de un ETag fuerte) de las partes dadas, en ese orden. */
    public static String of(Object... parts) {
        StringBuilder text = new StringBuilder();
        for (Object part : parts) {
            text.append(part == null ? "\u0000" : part).append('|');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(hash, 0, 12) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * true si el cliente mandó If-Match y ninguno de sus valores coincide con el ETag actual (la comparación ignora el
     * prefijo W/ de los ETag débiles). Sin If-Match, o con "*", no hay nada que comparar.
     */
    public static boolean stale(String ifMatch, String current) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.trim().equals("*")) {
            return false;
        }
        for (String candidate : ifMatch.split(",")) {
            String tag = candidate.trim();
            if (tag.startsWith("W/")) {
                tag = tag.substring(2);
            }
            if (tag.equals(current)) {
                return false;
            }
        }
        return true;
    }

    /** true si el cliente ya tiene esta versión (If-None-Match con el ETag actual): se responde 304 sin cuerpo. */
    public static boolean notModified(String ifNoneMatch, String current) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.trim();
            if (tag.equals("*") || (tag.startsWith("W/") ? tag.substring(2) : tag).equals(current)) {
                return true;
            }
        }
        return false;
    }
}
