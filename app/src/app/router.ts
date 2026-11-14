import { useMemo, useSyncExternalStore } from "react";
import type { View } from "./types";

/**
 * Router mínimo sobre la History API: cada pantalla tiene su propia dirección (se puede compartir, recargar y volver con
 * el botón "atrás"). No necesita librerías: la tienda tiene pocas rutas y la lógica de cada pantalla sigue en App.
 */

const VIEW_PATHS: Record<View, string> = {
  home: "/",
  products: "/productos",
  offers: "/ofertas",
  categories: "/categorias",
  cart: "/carrito",
  login: "/ingresar",
  admin: "/admin",
  orders: "/mis-pedidos",
  wishlist: "/favoritos",
  account: "/mi-cuenta",
  confirmation: "/compra-confirmada",
  terms: "/terminos",
  privacy: "/privacidad",
  contact: "/contacto",
  faq: "/preguntas-frecuentes",
  paymentResult: "/pago/resultado",
};

const PRODUCT_PATH = /^\/productos\/([^/]+)$/;

export type Route = {
  view: View;
  /** Id del producto si la dirección es /productos/:id. */
  productId: string | null;
  /** La dirección no corresponde a ninguna pantalla (se muestra el inicio). */
  unknown: boolean;
};

/** Quita la barra final ("/carrito/" -> "/carrito") y deja la raíz como "/". */
const clean = (pathname: string) => {
  let end = pathname.length;
  while (end > 0 && pathname[end - 1] === "/") end--;
  return end === 0 ? "/" : pathname.slice(0, end);
};

export function parsePath(pathname: string): Route {
  const path = clean(pathname);
  const product = PRODUCT_PATH.exec(path);
  if (product) {
    try {
      return { view: "products", productId: decodeURIComponent(product[1]), unknown: false };
    } catch {
      return { view: "home", productId: null, unknown: true };
    }
  }
  const entry = (Object.entries(VIEW_PATHS) as [View, string][]).find(([, p]) => p === path);
  return entry ? { view: entry[0], productId: null, unknown: false } : { view: "home", productId: null, unknown: true };
}

/** Dirección de una pantalla (o de un producto), con parámetros opcionales (?categoria=perros). */
export function pathFor(view: View, params?: Record<string, string | null | undefined>): string {
  return withQuery(VIEW_PATHS[view], params);
}

export function productPath(productId: string): string {
  return `/productos/${encodeURIComponent(productId)}`;
}

export function withQuery(path: string, params?: Record<string, string | null | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value) query.set(key, value);
  }
  const text = query.toString();
  return text ? `${path}?${text}` : path;
}

// ---------------------------------------------------------------------------------------------------------------------

const CHANGE_EVENT = "petshop:navigate";
let internalNavigations = 0;

const subscribe = (listener: () => void) => {
  window.addEventListener("popstate", listener);
  window.addEventListener(CHANGE_EVENT, listener);
  return () => {
    window.removeEventListener("popstate", listener);
    window.removeEventListener(CHANGE_EVENT, listener);
  };
};

const snapshot = () => window.location.pathname + window.location.search;

/** Va a otra dirección. Con `replace` no suma una entrada al historial (útil para redirigir o ajustar filtros). */
export function navigate(to: string, options: { replace?: boolean } = {}) {
  if (to === snapshot()) return;
  if (options.replace) {
    window.history.replaceState(null, "", to);
  } else {
    window.history.pushState(null, "", to);
    internalNavigations += 1;
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

/** Vuelve a la pantalla anterior si el cliente venía de la tienda; si entró directo por un link, va a `fallback`. */
export function goBack(fallback: string) {
  if (internalNavigations > 0) {
    internalNavigations -= 1;
    window.history.back();
  } else {
    navigate(fallback);
  }
}

export type Location = { pathname: string; search: string; params: URLSearchParams };

/** Dirección actual, que se actualiza al navegar y con los botones adelante/atrás del navegador. */
export function useLocation(): Location {
  const current = useSyncExternalStore(subscribe, snapshot, () => "/");
  return useMemo(() => {
    const [pathname, search = ""] = current.split("?");
    return { pathname, search: search ? `?${search}` : "", params: new URLSearchParams(search) };
  }, [current]);
}
