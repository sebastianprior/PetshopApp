import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createVenta, fetchCategories, getGuestId, login, UNAUTHORIZED_EVENT } from "../api";

function mockFetchOnce(status: number, body: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
    json: async () => body,
  });
}

describe("api request wrapper", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns parsed JSON on a successful response", async () => {
    vi.stubGlobal("fetch", mockFetchOnce(200, [{ id: "alimentos", name: "Alimentos" }]));

    const categories = await fetchCategories();

    expect(categories).toEqual([{ id: "alimentos", name: "Alimentos" }]);
  });

  it("throws the backend's error message on a non-2xx response", async () => {
    vi.stubGlobal("fetch", mockFetchOnce(400, { error: "Producto no encontrado" }));

    await expect(fetchCategories()).rejects.toThrow("Producto no encontrado");
  });

  it("dispatches UNAUTHORIZED_EVENT on a 401 response", async () => {
    vi.stubGlobal("fetch", mockFetchOnce(401, { error: "No autorizado" }));
    const handler = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, handler);

    await expect(fetchCategories()).rejects.toThrow();

    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(UNAUTHORIZED_EVENT, handler);
  });

  it("does not dispatch UNAUTHORIZED_EVENT for login (skipAuthRedirect)", async () => {
    vi.stubGlobal("fetch", mockFetchOnce(401, { error: "Credenciales inválidas" }));
    const handler = vi.fn();
    window.addEventListener(UNAUTHORIZED_EVENT, handler);

    await expect(login("a@a.com", "wrong")).rejects.toThrow("Credenciales inválidas");

    expect(handler).not.toHaveBeenCalled();
    window.removeEventListener(UNAUTHORIZED_EVENT, handler);
  });
});

describe("createVenta", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends the Idempotency-Key and payment token, and maps the venta to a CheckoutResult", async () => {
    const fetchMock = mockFetchOnce(201, {
      id: 7,
      estado: "COMPLETADA",
      estadoPago: "APROBADO",
      items: [{ productId: "p1", name: "Alimento", variant: "1kg", variantId: 3, quantity: 2, price: 1000 }],
      subtotal: 2000,
      shippingCost: 1500,
      discountAmount: 0,
      couponCode: null,
      total: 3500,
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await createVenta("tok", { nombre: "Ana" }, "DESC10", "tok_aprobado", "key-123");

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain("/v1/ventas");
    expect(init.headers.get("Idempotency-Key")).toBe("key-123");
    expect(init.headers.get("X-Auth-Token")).toBe("tok");
    expect(JSON.parse(init.body)).toEqual({ nombre: "Ana", cupon: "DESC10", medioPago: "tok_aprobado" });
    expect(result).toMatchObject({ ok: true, orderId: 7, estado: "COMPLETADA", estadoPago: "APROBADO", total: 3500 });
  });

  it("surfaces the Problem Details 'detail' as the error message", async () => {
    vi.stubGlobal("fetch", mockFetchOnce(402, { type: "urn:petshop:problem:pago-rechazado", title: "Pago rechazado", detail: "Fondos insuficientes" }));

    await expect(createVenta("tok", {}, null, "tok_rechazado", "k")).rejects.toThrow("Fondos insuficientes");
  });
});

describe("getGuestId", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("generates and persists a guest id on first call", () => {
    const id = getGuestId();

    expect(id).toBeTruthy();
    expect(localStorage.getItem("petshop_guest_id")).toBe(id);
  });

  it("returns the same id on subsequent calls", () => {
    const first = getGuestId();
    const second = getGuestId();

    expect(second).toBe(first);
  });
});
