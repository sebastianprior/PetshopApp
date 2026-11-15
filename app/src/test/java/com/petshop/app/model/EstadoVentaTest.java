package com.petshop.app.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Patrón State de la venta: la tabla completa de transiciones (4 estados x 3 acciones) y el rechazo de todas las demás. */
class EstadoVentaTest {

    private enum Accion {
        CONFIRMAR("confirmar", EstadoVenta::confirmar),
        ENVIAR("enviar", EstadoVenta::enviar),
        CANCELAR("cancelar", EstadoVenta::cancelar);

        final String nombre;
        final Function<EstadoVenta, EstadoVenta> aplicar;

        Accion(String nombre, Function<EstadoVenta, EstadoVenta> aplicar) {
            this.nombre = nombre;
            this.aplicar = aplicar;
        }
    }

    static Stream<Arguments> permitidas() {
        return Stream.of(
                Arguments.of(EstadoVenta.PAGO_PENDIENTE, Accion.CONFIRMAR, EstadoVenta.COMPLETADA),
                Arguments.of(EstadoVenta.PAGO_PENDIENTE, Accion.CANCELAR, EstadoVenta.CANCELADA),
                Arguments.of(EstadoVenta.COMPLETADA, Accion.ENVIAR, EstadoVenta.ENVIADA),
                Arguments.of(EstadoVenta.COMPLETADA, Accion.CANCELAR, EstadoVenta.CANCELADA));
    }

    static Stream<Arguments> rechazadas() {
        return Stream.of(
                Arguments.of(EstadoVenta.PAGO_PENDIENTE, Accion.ENVIAR),
                Arguments.of(EstadoVenta.COMPLETADA, Accion.CONFIRMAR),
                Arguments.of(EstadoVenta.ENVIADA, Accion.CONFIRMAR),
                Arguments.of(EstadoVenta.ENVIADA, Accion.ENVIAR),
                Arguments.of(EstadoVenta.ENVIADA, Accion.CANCELAR),
                Arguments.of(EstadoVenta.CANCELADA, Accion.CONFIRMAR),
                Arguments.of(EstadoVenta.CANCELADA, Accion.ENVIAR),
                Arguments.of(EstadoVenta.CANCELADA, Accion.CANCELAR));
    }

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @MethodSource("permitidas")
    void anAllowedActionMovesToTheNextState(EstadoVenta desde, Accion accion, EstadoVenta esperado) {
        assertThat(accion.aplicar.apply(desde)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} no admite {1}")
    @MethodSource("rechazadas")
    void anyOtherActionIsRejectedWithTheStateAndTheActionInTheMessage(EstadoVenta desde, Accion accion) {
        assertThatThrownBy(() -> accion.aplicar.apply(desde))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining(desde.name())
                .hasMessageContaining(accion.nombre)
                .satisfies(e -> {
                    TransicionInvalidaException ex = (TransicionInvalidaException) e;
                    assertThat(ex.estado()).isEqualTo(desde.name());
                    assertThat(ex.accion()).isEqualTo(accion.nombre);
                });
    }

    @Test
    void theTableCoversEveryCombinationExactlyOnce() {
        assertThat(permitidas().count() + rechazadas().count())
                .isEqualTo((long) EstadoVenta.values().length * Accion.values().length);
    }

    @Test
    void shippedAndCancelledAreTerminal() {
        for (EstadoVenta terminal : new EstadoVenta[] {EstadoVenta.ENVIADA, EstadoVenta.CANCELADA}) {
            for (Accion accion : Accion.values()) {
                assertThatThrownBy(() -> accion.aplicar.apply(terminal)).isInstanceOf(TransicionInvalidaException.class);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAGO_PENDIENTE", "COMPLETADA", "ENVIADA", "CANCELADA"})
    void aStoredValueMapsToItsState(String guardado) {
        assertThat(EstadoVenta.de(guardado)).contains(EstadoVenta.valueOf(guardado));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PENDIENTE", "enviada", " COMPLETADA", "BASURA"})
    void anythingElseIsNotAState(String guardado) {
        assertThat(EstadoVenta.de(guardado)).isEmpty();
    }
}
