import { describe, expect, it } from "vitest";
import {
  validateAddress,
  validateCity,
  validateCouponCode,
  validateName,
  validatePhone,
  validatePostalCode,
  validateShipping,
} from "../checkoutValidation";

describe("validadores del checkout", () => {
  describe("nombre", () => {
    it.each(["Cliente", "Cliente Demo", "María José Pérez-Gómez", "D'Angelo", "Ana M. López"])("acepta %s", (value) => {
      expect(validateName(value)).toBeNull();
    });
    it.each([
      ["", /Ingresá/],
      ["   ", /Ingresá/],
      ["A", /corto/],
      ["Juan123", /letras/],
      ["<script>alert(1)</script>", /letras/],
      ["a".repeat(81), /80/],
    ])("rechaza %j", (value, message) => {
      expect(validateName(value)).toMatch(message);
    });
  });

  describe("dirección", () => {
    it.each(["Calle 1", "Av. Corrientes 1234, 5° B", "Mitre 456 piso 2/dto. 4", "Calle Falsa 123"])("acepta %s", (value) => {
      expect(validateAddress(value)).toBeNull();
    });
    it.each([
      ["", /Ingresá/],
      ["Av 1", /corta/],
      ["<img src=x onerror=alert(1)>", /no permitidos/],
      ["12345678", /calle/],
      ["Calle " + "1".repeat(120), /120/],
    ])("rechaza %j", (value, message) => {
      expect(validateAddress(value)).toMatch(message);
    });
  });

  describe("ciudad", () => {
    it.each(["CABA", "Buenos Aires", "San Martín de los Andes"])("acepta %s", (value) => {
      expect(validateCity(value)).toBeNull();
    });
    it.each([["", /Ingresá/], ["B", /letras/], ["Ciudad 2", /letras/], ["<b>CABA</b>", /letras/], ["a".repeat(61), /60/]])(
      "rechaza %j",
      (value, message) => {
        expect(validateCity(value)).toMatch(message);
      },
    );
  });

  describe("teléfono (opcional)", () => {
    it.each(["", "  ", "1145678901", "+54 11 4567-8901", "(011) 4567 8901"])("acepta %j", (value) => {
      expect(validatePhone(value)).toBeNull();
    });
    it.each(["12345", "abc123456", "1234567890123456", "11 4567 <8901>", "+++++++"])("rechaza %j", (value) => {
      expect(validatePhone(value)).toMatch(/entre 6 y 15 números/);
    });
  });

  describe("código postal", () => {
    it.each(["1425", "C1425ABC", "c1425abc"])("acepta %s", (value) => {
      expect(validatePostalCode(value)).toBeNull();
    });
    it.each([["", /Ingresá/], ["12", /4 números/], ["14x5", /4 números/], ["14256", /4 números/]])("rechaza %j", (value, message) => {
      expect(validatePostalCode(value)).toMatch(message);
    });
  });

  describe("cupón (opcional)", () => {
    it.each(["", "DESC10", "bienvenida-10", "PROMO_2026"])("acepta %j", (value) => {
      expect(validateCouponCode(value)).toBeNull();
    });
    it.each(["DESC 10", "10%", "cupon<script>", "a".repeat(31)])("rechaza %j", (value) => {
      expect(validateCouponCode(value)).toMatch(/solo puede tener/);
    });
  });

  it("validateShipping devuelve solo los campos inválidos", () => {
    const errors = validateShipping({ nombre: "Ana López", direccion: "x", ciudad: "CABA", codigoPostal: "12", telefono: "" });
    expect(Object.keys(errors).sort()).toEqual(["codigoPostal", "direccion"]);
  });

  it("validateShipping no devuelve errores con datos correctos", () => {
    expect(validateShipping({ nombre: "Ana López", direccion: "Calle Falsa 123", ciudad: "CABA", codigoPostal: "1425", telefono: "" })).toEqual({});
  });
});
