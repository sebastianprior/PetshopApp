import type { ShippingInfo } from "./types";

/** Una cadena de error por campo inválido; los campos correctos no aparecen. */
export type ShippingErrors = Partial<Record<keyof ShippingInfo, string>>;

const NAME_PATTERN = /^\p{L}[\p{L} .'’-]*$/u;
const ADDRESS_PATTERN = /^[\p{L}\d][\p{L}\d .,'’#°ºª/-]*$/u;
const PHONE_PATTERN = /^[\d ()+-]+$/;
const COUPON_PATTERN = /^[A-Za-z0-9_-]+$/;
/** Código postal argentino: 4 números, con o sin letras (C1425ABC). */
export const POSTAL_CODE_PATTERN = /^[A-Za-z]?\d{4}([A-Za-z]{3})?$/;

export const LIMITS = { nombre: 80, direccion: 120, ciudad: 60, telefono: 25, cupon: 30 } as const;

const countLetters = (value: string) => (value.match(/\p{L}/gu) ?? []).length;
const countDigits = (value: string) => (value.match(/\d/g) ?? []).length;

export function validateName(raw: string): string | null {
  const value = raw.trim();
  if (!value) return "Ingresá tu nombre y apellido.";
  if (value.length > LIMITS.nombre) return `El nombre no puede superar los ${LIMITS.nombre} caracteres.`;
  if (!NAME_PATTERN.test(value)) return "Usá solo letras, espacios, puntos, apóstrofes y guiones.";
  if (countLetters(value) < 2) return "El nombre es demasiado corto.";
  return null;
}

export function validateAddress(raw: string): string | null {
  const value = raw.trim();
  if (!value) return "Ingresá tu dirección.";
  if (value.length < 5) return "La dirección es demasiado corta: incluí calle y número.";
  if (value.length > LIMITS.direccion) return `La dirección no puede superar los ${LIMITS.direccion} caracteres.`;
  if (!ADDRESS_PATTERN.test(value)) return "La dirección tiene caracteres no permitidos.";
  if (countLetters(value) < 2) return "Incluí el nombre de la calle.";
  return null;
}

export function validateCity(raw: string): string | null {
  const value = raw.trim();
  if (!value) return "Ingresá tu ciudad.";
  if (value.length > LIMITS.ciudad) return `La ciudad no puede superar los ${LIMITS.ciudad} caracteres.`;
  if (!NAME_PATTERN.test(value) || countLetters(value) < 2) return "Usá solo letras, espacios, puntos, apóstrofes y guiones.";
  return null;
}

/** El teléfono es opcional: vacío es válido. */
export function validatePhone(raw: string): string | null {
  const value = raw.trim();
  if (!value) return null;
  const digits = countDigits(value);
  if (value.length > LIMITS.telefono || !PHONE_PATTERN.test(value) || digits < 6 || digits > 15) {
    return "El teléfono debe tener entre 6 y 15 números (podés usar espacios, guiones, paréntesis y +).";
  }
  return null;
}

export function validatePostalCode(raw: string): string | null {
  const value = raw.trim();
  if (!value) return "Ingresá tu código postal.";
  if (!POSTAL_CODE_PATTERN.test(value)) return "Revisá el código postal: son 4 números (por ejemplo, 1425).";
  return null;
}

/** El cupón es opcional: vacío es válido. */
export function validateCouponCode(raw: string): string | null {
  const value = raw.trim();
  if (!value) return null;
  if (value.length > LIMITS.cupon || !COUPON_PATTERN.test(value)) {
    return "El cupón solo puede tener letras, números, guiones y guiones bajos.";
  }
  return null;
}

export function validateShipping(shipping: ShippingInfo): ShippingErrors {
  const errors: ShippingErrors = {};
  const checks: [keyof ShippingInfo, string | null][] = [
    ["nombre", validateName(shipping.nombre)],
    ["direccion", validateAddress(shipping.direccion)],
    ["ciudad", validateCity(shipping.ciudad)],
    ["codigoPostal", validatePostalCode(shipping.codigoPostal)],
    ["telefono", validatePhone(shipping.telefono)],
  ];
  for (const [field, message] of checks) {
    if (message) errors[field] = message;
  }
  return errors;
}
