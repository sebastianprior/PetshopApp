package com.petshop.app.model;

/** Se intentó una acción que el estado actual de la venta no permite. La API la responde como 409 Conflict. */
public class TransicionInvalidaException extends IllegalStateException {

    private final String estado;
    private final String accion;

    public TransicionInvalidaException(EstadoVenta estado, String accion) {
        this(estado.name(), accion);
    }

    public TransicionInvalidaException(String estado, String accion) {
        super("Una venta en estado " + estado + " no admite la acción '" + accion + "'.");
        this.estado = estado;
        this.accion = accion;
    }

    public String estado() {
        return estado;
    }

    public String accion() {
        return accion;
    }
}
