package com.petshop.app.service;

import com.petshop.app.dto.VentaRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatosEnvioValidatorTest {

    private static VentaRequest valida() {
        VentaRequest req = new VentaRequest();
        req.nombre = "María José Pérez-Gómez";
        req.direccion = "Av. Corrientes 1234, 5° B";
        req.ciudad = "Ciudad Autónoma de Buenos Aires";
        req.codigoPostal = "1425";
        req.telefono = "+54 11 4567-8901";
        req.cupon = "BIENVENIDA10";
        return req;
    }

    @Test
    void aceptaDatosHabitualesConTildesPuntosYGuiones() {
        assertThat(DatosEnvioValidator.error(valida())).isNull();
    }

    @Test
    void aceptaTelefonoYCuponVacios() {
        VentaRequest req = valida();
        req.telefono = null;
        req.cupon = "  ";
        assertThat(DatosEnvioValidator.error(req)).isNull();
    }

    @Test
    void aceptaLosDatosCortosQueYaUsabanLosClientesDeLaApi() {
        VentaRequest req = new VentaRequest();
        req.nombre = "A";
        req.direccion = "D 1";
        req.ciudad = "CABA";
        assertThat(DatosEnvioValidator.error(req)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>alert(1)</script>", "Juan123", "12345", "Juan; DROP TABLE", "Ana\nLópez", "  <b>x</b>"})
    void rechazaNombresConSimbolosONumeros(String nombre) {
        VentaRequest req = valida();
        req.nombre = nombre;
        assertThat(DatosEnvioValidator.error(req)).contains("nombre");
    }

    @Test
    void rechazaUnNombreDeMasDeOchentaCaracteres() {
        VentaRequest req = valida();
        req.nombre = "a".repeat(81);
        assertThat(DatosEnvioValidator.error(req)).contains("nombre");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<img src=x onerror=alert(1)>", "Calle 1; --", "Calle\u0000 1", "$(rm -rf)"})
    void rechazaDireccionesConCaracteresPeligrosos(String direccion) {
        VentaRequest req = valida();
        req.direccion = direccion;
        assertThat(DatosEnvioValidator.error(req)).contains("dirección");
    }

    @Test
    void rechazaUnaDireccionDeMasDeCientoVeinteCaracteres() {
        VentaRequest req = valida();
        req.direccion = "Calle " + "1".repeat(120);
        assertThat(DatosEnvioValidator.error(req)).contains("dirección");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Buenos Aires 2", "<b>CABA</b>", "1234"})
    void rechazaCiudadesConNumerosOSimbolos(String ciudad) {
        VentaRequest req = valida();
        req.ciudad = ciudad;
        assertThat(DatosEnvioValidator.error(req)).contains("ciudad");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "abc123456", "1234567890123456", "11 4567 <8901>", "+++++++"})
    void rechazaTelefonosInvalidos(String telefono) {
        VentaRequest req = valida();
        req.telefono = telefono;
        assertThat(DatosEnvioValidator.error(req)).contains("teléfono");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUPON 10", "cupon<script>", "10%"})
    void rechazaCuponesConFormatoInvalido(String cupon) {
        VentaRequest req = valida();
        req.cupon = cupon;
        assertThat(DatosEnvioValidator.error(req)).contains("cupón");
    }

    @Test
    void rechazaUnCuponDeMasDeTreintaCaracteres() {
        VentaRequest req = valida();
        req.cupon = "A".repeat(31);
        assertThat(DatosEnvioValidator.error(req)).contains("cupón");
    }
}
