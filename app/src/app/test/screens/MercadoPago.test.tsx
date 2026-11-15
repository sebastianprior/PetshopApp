import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CartScreen } from "../../screens/CartScreen";
import { PaymentResultScreen } from "../../screens/PaymentResultScreen";
import { fetchRouletteCartDiscount, fetchShippingQuote, syncVentaPayment } from "../../api";
import type { CartItem, CheckoutResult } from "../../types";

vi.mock("../../api", () => ({
  validateCoupon: vi.fn(),
  fetchRouletteCartDiscount: vi.fn(),
  fetchShippingQuote: vi.fn(),
  syncVentaPayment: vi.fn(),
}));

const mockedSync = vi.mocked(syncVentaPayment);

const ITEM: CartItem = { productId: "p1", name: "Croquetas", variant: "alimentos", variantId: null, quantity: 1, price: 1000 };

function result(estado: string, extra: Partial<CheckoutResult> = {}): CheckoutResult {
  return {
    ok: true,
    items: [{ ...ITEM }],
    subtotal: 1000,
    shippingCost: 1200,
    discountAmount: 0,
    couponCode: null,
    total: 2200,
    orderId: 12,
    estado,
    estadoPago: estado === "COMPLETADA" ? "APROBADO" : "PENDIENTE",
    ...extra,
  };
}

describe("CartScreen: medios de pago", () => {
  beforeEach(() => {
    vi.mocked(fetchRouletteCartDiscount).mockResolvedValue({ percent: 10, amount: 0, categoryId: null, categoryName: null });
    vi.mocked(fetchShippingQuote).mockResolvedValue({ postalCode: 1425, zoneName: "CABA", cost: 1200, eta: "24 a 48 hs", matchedZone: true });
    localStorage.clear();
  });

  const renderCart = (paymentConfig?: { mercadoPago: boolean; simulated: boolean }, onCheckout = vi.fn()) =>
    render(
      <CartScreen
        items={[ITEM]}
        productImages={{}}
        authToken={null}
        paymentConfig={paymentConfig}
        onRemove={vi.fn()}
        onCheckout={onCheckout}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

  it("keeps offering only the test payment methods when MercadoPago is not configured", () => {
    renderCart();

    expect(screen.getByLabelText("Medio de pago (simulado)")).toHaveValue("tok_aprobado");
    expect(screen.queryByRole("option", { name: /MercadoPago/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Finalizar compra" })).toBeInTheDocument();
  });

  it("offers MercadoPago first and selects it by default when it is available", () => {
    renderCart({ mercadoPago: true, simulated: true });

    expect(screen.getByLabelText("Medio de pago")).toHaveValue("mercadopago");
    expect(screen.getByRole("option", { name: /MercadoPago/ })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Tarjeta de prueba: aprobada" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pagar con MercadoPago" })).toBeInTheDocument();
  });

  it("hides the test methods in production (only MercadoPago)", () => {
    renderCart({ mercadoPago: true, simulated: false });

    expect(screen.getByRole("option", { name: /MercadoPago/ })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Tarjeta de prueba/ })).not.toBeInTheDocument();
  });

  it("sends mercadopago as the payment method on checkout", async () => {
    const user = userEvent.setup();
    const onCheckout = vi.fn();
    renderCart({ mercadoPago: true, simulated: true }, onCheckout);

    await user.type(screen.getByLabelText("Nombre y apellido"), "Cliente");
    await user.type(screen.getByLabelText("Dirección"), "Calle 1");
    await user.type(screen.getByLabelText("Ciudad"), "CABA");
    await user.type(screen.getByLabelText("Calculá el costo de tu envío"), "1425");
    await user.click(screen.getByRole("button", { name: "Pagar con MercadoPago" }));

    expect(onCheckout).toHaveBeenCalledWith(expect.objectContaining({ codigoPostal: "1425" }), undefined, "mercadopago");
  });

  it("switches to MercadoPago when the payment methods arrive after the cart opened, unless the customer already chose", async () => {
    const user = userEvent.setup();
    const props = {
      items: [ITEM],
      productImages: {},
      authToken: null,
      onRemove: vi.fn(),
      onCheckout: vi.fn(),
      onIncrement: vi.fn(),
      onDecrement: vi.fn(),
    };
    const { rerender } = render(<CartScreen {...props} />);
    expect(screen.getByLabelText("Medio de pago (simulado)")).toHaveValue("tok_aprobado");

    rerender(<CartScreen {...props} paymentConfig={{ mercadoPago: true, simulated: true }} />);
    expect(screen.getByLabelText("Medio de pago")).toHaveValue("mercadopago");

    await user.selectOptions(screen.getByLabelText("Medio de pago"), "tok_rechazado");
    rerender(<CartScreen {...props} paymentConfig={{ mercadoPago: true, simulated: true }} />);
    expect(screen.getByLabelText("Medio de pago")).toHaveValue("tok_rechazado");
  });
});

describe("PaymentResultScreen", () => {
  beforeEach(() => {
    mockedSync.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  const renderResult = (props: Partial<Parameters<typeof PaymentResultScreen>[0]> = {}) =>
    render(<PaymentResultScreen orderId={12} isLoggedIn onNavigate={vi.fn()} {...props} />);

  it("confirms the payment with the server and shows the purchase as confirmed", async () => {
    mockedSync.mockResolvedValue(result("COMPLETADA"));
    renderResult();

    expect(screen.getByText("Confirmando tu pago...")).toBeInTheDocument();
    expect(await screen.findByText("¡Compra confirmada!")).toBeInTheDocument();
    expect(mockedSync).toHaveBeenCalledWith(12);
    expect(screen.getByText(/pedido #12/)).toBeInTheDocument();
  });

  it("explains that the order was cancelled when the payment was never completed", async () => {
    const onNavigate = vi.fn();
    mockedSync.mockResolvedValue(result("CANCELADA"));
    renderResult({ onNavigate });

    expect(await screen.findByText("El pago no se completó")).toBeInTheDocument();
    expect(screen.getByText(/no se te cobró nada/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Volver a la tienda" }));
    expect(onNavigate).toHaveBeenCalledWith("products");
  });

  it("keeps the sale waiting and lets the customer go back to pay or check again", async () => {
    mockedSync.mockResolvedValue(result("PAGO_PENDIENTE", { paymentUrl: "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=p" }));
    renderResult();

    expect(await screen.findByText("Todavía esperamos la confirmación del pago")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ir a pagar" })).toHaveAttribute("href", expect.stringContaining("pref_id=p"));
    expect(screen.getByRole("button", { name: "Verificar de nuevo" })).toBeInTheDocument();
  });

  it("tells the customer the payment was not approved when MercadoPago sent them back with a rejection", async () => {
    mockedSync.mockResolvedValue(result("PAGO_PENDIENTE", { paymentUrl: "https://mp/pay" }));
    renderResult({ returnedStatus: "rejected" });

    expect(await screen.findByText("El pago no se completó")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Intentar de nuevo" })).toHaveAttribute("href", "https://mp/pay");
  });

  it("checks again by itself while the payment is pending and shows the result when it is approved", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockedSync.mockResolvedValueOnce(result("PAGO_PENDIENTE", { paymentUrl: "https://mp/pay" })).mockResolvedValue(result("COMPLETADA"));
    renderResult();
    expect(await screen.findByText("Todavía esperamos la confirmación del pago")).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5200);
    });

    expect(await screen.findByText("¡Compra confirmada!")).toBeInTheDocument();
    expect(mockedSync).toHaveBeenCalledTimes(2);
  });

  it("lets the customer retry when the payment could not be verified", async () => {
    const user = userEvent.setup();
    mockedSync.mockRejectedValueOnce(new Error("Sin conexión")).mockResolvedValueOnce(result("COMPLETADA"));
    renderResult();

    expect(await screen.findByText("No pudimos verificar tu pago")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Verificar de nuevo" }));

    await waitFor(() => expect(screen.getByText("¡Compra confirmada!")).toBeInTheDocument());
  });

  it("handles a return without a valid order number", () => {
    renderResult({ orderId: null, isLoggedIn: false });

    expect(screen.getByText("No encontramos ese pedido")).toBeInTheDocument();
    expect(mockedSync).not.toHaveBeenCalled();
  });
});
