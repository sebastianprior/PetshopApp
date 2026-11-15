import type { Product } from "../types";

/** Porcentaje de descuento real de un producto (promoción vigente o precio anterior tachado). */
export function discountOf(product: Product): number {
  const current = product.precioPromocional ?? product.price;
  const previous = product.precioPromocional != null ? product.price : product.oldPrice;
  return previous && previous > current ? Math.round((1 - current / previous) * 100) : 0;
}

/** ¿Es una oferta? Tiene descuento real o una etiqueta de oferta. Es el mismo criterio en Inicio y en Ofertas. */
export function isOffer(product: Product): boolean {
  const hasPromoBadge = Boolean(product.badge && /(oferta|promo|descuento|sale)/i.test(product.badge));
  return discountOf(product) > 0 || hasPromoBadge;
}
