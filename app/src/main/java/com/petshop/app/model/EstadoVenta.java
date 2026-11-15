package com.petshop.app.model;

import java.util.Optional;

/**
 * Patrón State para el ciclo de vida de una venta (ver docs/adr/0003-patron-state-en-la-venta.md).
 *
 * <p>Cada estado es el único que sabe a qué estado puede pasar la venta ante cada acción. Lo que no define, lo rechaza con
 * {@link TransicionInvalidaException}, que la API responde como <b>409 Conflict</b> (Problem Details). Así ninguna regla de
 * transición queda repartida en {@code if} sueltos por los servicios y controladores.
 *
 * <pre>
 *   PAGO_PENDIENTE --confirmar--> COMPLETADA --enviar--> ENVIADA
 *        |                            |
 *        +--------cancelar------------+--------> CANCELADA
 * </pre>
 *
 * ENVIADA y CANCELADA son terminales: no admiten ninguna acción.
 */
public enum EstadoVenta {

    /** Venta creada y con el stock reservado, esperando que la pasarela o MercadoPago confirme el pago. */
    PAGO_PENDIENTE {
        @Override
        public EstadoVenta confirmar() {
            return COMPLETADA;
        }

        @Override
        public EstadoVenta cancelar() {
            return CANCELADA;
        }
    },

    /** Pago aprobado: se puede despachar o cancelar (con reembolso si ya se cobró). */
    COMPLETADA {
        @Override
        public EstadoVenta enviar() {
            return ENVIADA;
        }

        @Override
        public EstadoVenta cancelar() {
            return CANCELADA;
        }
    },

    /** Despachada: estado terminal (una devolución es otro proceso, con su propia entidad). */
    ENVIADA,

    /** Cancelada, con el stock liberado: estado terminal. */
    CANCELADA;

    /** El pago se aprobó. */
    public EstadoVenta confirmar() {
        throw new TransicionInvalidaException(this, "confirmar");
    }

    /** La venta se despacha. */
    public EstadoVenta enviar() {
        throw new TransicionInvalidaException(this, "enviar");
    }

    /** La venta se cancela (por el cliente, por el administrador o porque el pago no se completó). */
    public EstadoVenta cancelar() {
        throw new TransicionInvalidaException(this, "cancelar");
    }

    /** El estado que corresponde al texto guardado en la venta (distingue mayúsculas); vacío si no existe. */
    public static Optional<EstadoVenta> de(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        for (EstadoVenta estado : values()) {
            if (estado.name().equals(valor)) {
                return Optional.of(estado);
            }
        }
        return Optional.empty();
    }
}
