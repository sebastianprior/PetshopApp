package com.petshop.app.service;

/** Reglas de contraseña compartidas por registro, cambio y recuperación. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 6;
    /** BCrypt solo usa los primeros 72 bytes de la contraseña. */
    public static final int MAX_LENGTH = 72;

    private PasswordPolicy() {}

    /** @return el mensaje de error, o null si la contraseña es válida */
    public static String validate(String password) {
        if (password == null || password.isBlank()) {
            return "La contraseña es obligatoria";
        }
        if (password.length() < MIN_LENGTH) {
            return "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres";
        }
        if (password.length() > MAX_LENGTH) {
            return "La contraseña puede tener hasta " + MAX_LENGTH + " caracteres";
        }
        return null;
    }
}
