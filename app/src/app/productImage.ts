import type { SyntheticEvent } from "react";

/** Dibujo genérico que se usa cuando un producto no tiene imagen o la imagen no carga. */
export const PLACEHOLDER_IMAGE = "/products/placeholder.svg";

/** onError de las imágenes de producto: si la URL falla, se muestra el dibujo genérico (una sola vez). */
export function handleImageError(event: SyntheticEvent<HTMLImageElement>) {
  const image = event.currentTarget;
  if (!image.src.endsWith(PLACEHOLDER_IMAGE)) {
    image.src = PLACEHOLDER_IMAGE;
  }
}
