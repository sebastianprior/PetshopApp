import { beforeEach, describe, expect, it } from "vitest";
import { waitFor } from "@testing-library/react";
import { goBack, navigate, parsePath, pathFor, productPath, withQuery } from "../router";

describe("parsePath", () => {
  it("maps every screen address to its view", () => {
    expect(parsePath("/")).toEqual({ view: "home", productId: null, unknown: false });
    expect(parsePath("/productos").view).toBe("products");
    expect(parsePath("/ofertas").view).toBe("offers");
    expect(parsePath("/carrito").view).toBe("cart");
    expect(parsePath("/ingresar").view).toBe("login");
    expect(parsePath("/admin").view).toBe("admin");
    expect(parsePath("/mis-pedidos").view).toBe("orders");
    expect(parsePath("/favoritos").view).toBe("wishlist");
    expect(parsePath("/mi-cuenta").view).toBe("account");
    expect(parsePath("/compra-confirmada").view).toBe("confirmation");
    expect(parsePath("/terminos").view).toBe("terms");
    expect(parsePath("/privacidad").view).toBe("privacy");
    expect(parsePath("/contacto").view).toBe("contact");
    expect(parsePath("/preguntas-frecuentes").view).toBe("faq");
    expect(parsePath("/pago/resultado").view).toBe("paymentResult");
  });

  it("reads the product id from /productos/:id, including special characters", () => {
    expect(parsePath("/productos/p12")).toEqual({ view: "products", productId: "p12", unknown: false });
    expect(parsePath("/productos/a%20b").productId).toBe("a b");
  });

  it("ignores a trailing slash", () => {
    expect(parsePath("/carrito/").view).toBe("cart");
    expect(parsePath("/productos/p1/").productId).toBe("p1");
  });

  it("flags addresses that do not exist and falls back to the home", () => {
    expect(parsePath("/no-existe")).toEqual({ view: "home", productId: null, unknown: true });
    expect(parsePath("/productos/p1/extra").unknown).toBe(true);
    expect(parsePath("/productos/%E0%A4%A").unknown).toBe(true);
  });
});

describe("pathFor", () => {
  it("builds addresses with optional query parameters, skipping empty ones", () => {
    expect(pathFor("home")).toBe("/");
    expect(pathFor("cart")).toBe("/carrito");
    expect(pathFor("products", { categoria: "perros", q: "" })).toBe("/productos?categoria=perros");
    expect(pathFor("products", { categoria: null, q: "collar rojo" })).toBe("/productos?q=collar+rojo");
    expect(pathFor("products", { categoria: undefined })).toBe("/productos");
    expect(productPath("p 1")).toBe("/productos/p%201");
    expect(withQuery("/ingresar", { next: "/mis-pedidos" })).toBe("/ingresar?next=%2Fmis-pedidos");
  });

  it("round-trips with parsePath", () => {
    for (const view of ["home", "products", "offers", "cart", "login", "admin", "orders", "wishlist", "account", "faq"] as const) {
      expect(parsePath(pathFor(view)).view).toBe(view);
    }
    expect(parsePath(productPath("p/7")).productId).toBe("p/7");
  });
});

describe("navigate and goBack", () => {
  beforeEach(() => {
    window.history.replaceState(null, "", "/");
  });

  // Va primero: el router cuenta cuántas veces se navegó dentro de la tienda y este caso necesita que sea cero
  it("goes to the fallback when the customer arrived directly through a link", () => {
    window.history.replaceState(null, "", "/productos/p1");

    goBack("/productos");

    expect(window.location.pathname).toBe("/productos");
  });

  it("changes the address and can replace it without adding a history entry", () => {
    const before = window.history.length;
    navigate("/carrito");
    expect(window.location.pathname).toBe("/carrito");
    expect(window.history.length).toBe(before + 1);

    navigate("/ofertas", { replace: true });
    expect(window.location.pathname).toBe("/ofertas");
    expect(window.history.length).toBe(before + 1);
  });

  it("does nothing when already there", () => {
    navigate("/faq-test");
    const length = window.history.length;
    navigate("/faq-test");
    expect(window.history.length).toBe(length);
  });

  it("goes back to the previous screen when the customer came from inside the store", async () => {
    navigate("/productos");
    navigate("/productos/p1");

    goBack("/otra");

    await waitFor(() => expect(window.location.pathname).toBe("/productos"));
  });
});
