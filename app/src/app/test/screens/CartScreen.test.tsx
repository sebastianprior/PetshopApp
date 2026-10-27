import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CartScreen } from "../../screens/CartScreen";
import { NO_CART_PROMOS, type CartItem } from "../../types";
import { fetchRouletteCartDiscount, fetchShippingQuote, validateCoupon } from "../../api";

vi.mock("../../api", () => ({
  validateCoupon: vi.fn(),
  fetchShippingQuote: vi.fn(),
  fetchRouletteCartDiscount: vi.fn().mockResolvedValue({ percent: 10, amount: 0, categoryId: null, categoryName: null }),
}));

const mockedValidateCoupon = vi.mocked(validateCoupon);
const mockedRoulette = vi.mocked(fetchRouletteCartDiscount);
const mockedQuote = vi.mocked(fetchShippingQuote);

/** Escribe el código postal en el carrito y espera a que se calcule el envío. */
async function enterPostalCode(user: ReturnType<typeof userEvent.setup>, code = "1425") {
  await user.type(screen.getByLabelText("Calculá el costo de tu envío"), code);
  await screen.findByText(/llega en 24 a 48 hs/);
}

const ITEM: CartItem = {
  productId: "p1",
  name: "Croquetas SuperDog",
  variant: "alimentos",
  variantId: null,
  quantity: 2,
  price: 1000,
};

describe("CartScreen", () => {
  beforeEach(() => {
    mockedValidateCoupon.mockReset();
    mockedQuote.mockReset().mockResolvedValue({ postalCode: 1425, zoneName: "CABA", cost: 1500, eta: "24 a 48 hs", matchedZone: true });
    mockedRoulette.mockReset().mockResolvedValue({ percent: 10, amount: 0, categoryId: null, categoryName: null });
    localStorage.clear();
  });

  it("shows an empty state when there are no items", () => {
    render(
      <CartScreen
        items={[]}
        productImages={{}}
        authToken={null}
        onRemove={vi.fn()}
        onCheckout={vi.fn()}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    expect(screen.getByText("Tu carrito está vacío.")).toBeInTheDocument();
  });

  it("computes subtotal, shipping and total for the given items once the postal code is entered", async () => {
    const user = userEvent.setup();
    render(
      <CartScreen
        items={[ITEM]}
        productImages={{}}
        authToken={null}
        onRemove={vi.fn()}
        onCheckout={vi.fn()}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    // sin código postal el envío queda "A calcular" y no se suma al total
    expect(screen.getByText("A calcular")).toBeInTheDocument();
    expect(screen.getByText("Total (sin envío)")).toBeInTheDocument();
    expect(screen.getAllByText("$ 2.000,00")).toHaveLength(2);

    // subtotal = 1000 * 2 = 2000, shipping = 1500, total = 3500
    await enterPostalCode(user);
    expect(mockedQuote).toHaveBeenCalledWith("1425");
    expect(screen.getByText("$ 1.500,00")).toBeInTheDocument();
    expect(screen.getByText("$ 3.500,00")).toBeInTheDocument();
    expect(screen.queryByText("A calcular")).not.toBeInTheDocument();
  });

  it("calls onIncrement, onDecrement and onRemove with the clicked item", async () => {
    const user = userEvent.setup();
    const onIncrement = vi.fn();
    const onDecrement = vi.fn();
    const onRemove = vi.fn();

    render(
      <CartScreen
        items={[ITEM]}
        productImages={{}}
        authToken={null}
        onRemove={onRemove}
        onCheckout={vi.fn()}
        onIncrement={onIncrement}
        onDecrement={onDecrement}
      />,
    );

    await user.click(screen.getByLabelText("Sumar cantidad"));
    await user.click(screen.getByLabelText("Restar cantidad"));
    await user.click(screen.getByText("Quitar"));

    expect(onIncrement).toHaveBeenCalledWith(ITEM);
    expect(onDecrement).toHaveBeenCalledWith(ITEM);
    expect(onRemove).toHaveBeenCalledWith(ITEM);
  });

  it("applies a valid coupon and updates the total", async () => {
    const user = userEvent.setup();
    mockedValidateCoupon.mockResolvedValue({ code: "DESC10", discountAmount: 200 });

    render(
      <CartScreen
        items={[ITEM]}
        productImages={{}}
        authToken={null}
        onRemove={vi.fn()}
        onCheckout={vi.fn()}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    await enterPostalCode(user);
    await user.type(screen.getByPlaceholderText("Código de cupón"), "desc10");
    await user.click(screen.getByText("Aplicar"));

    await waitFor(() => expect(screen.getByText(/Cupón/)).toBeInTheDocument());
    expect(mockedValidateCoupon).toHaveBeenCalledWith("desc10", 2000);
    expect(screen.getByText("-$ 200,00")).toBeInTheDocument();
    // total = 2000 + 1500 - 200 = 3300
    expect(screen.getByText("$ 3.300,00")).toBeInTheDocument();
  });

  it("shows an error message when the coupon is rejected", async () => {
    const user = userEvent.setup();
    mockedValidateCoupon.mockRejectedValue(new Error("Cupón inválido"));

    render(
      <CartScreen
        items={[ITEM]}
        productImages={{}}
        authToken={null}
        onRemove={vi.fn()}
        onCheckout={vi.fn()}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    await user.type(screen.getByPlaceholderText("Código de cupón"), "NOPE");
    await user.click(screen.getByText("Aplicar"));

    await waitFor(() => expect(screen.getByText("Cupón inválido")).toBeInTheDocument());
  });

  it("passes the shipping data and applied coupon code to onCheckout", async () => {
    const user = userEvent.setup();
    const onCheckout = vi.fn();
    mockedValidateCoupon.mockResolvedValue({ code: "DESC10", discountAmount: 200 });

    render(
      <CartScreen
        items={[ITEM]}
        productImages={{}}
        authToken={null}
        onRemove={vi.fn()}
        onCheckout={onCheckout}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    await user.type(screen.getByPlaceholderText("Código de cupón"), "DESC10");
    await user.click(screen.getByText("Aplicar"));
    await waitFor(() => expect(screen.getByText(/Cupón/)).toBeInTheDocument());

    await user.type(screen.getByLabelText("Nombre y apellido"), "Cliente Demo");
    await user.type(screen.getByLabelText("Dirección"), "Calle Falsa 123");
    await user.type(screen.getByLabelText("Ciudad"), "CABA");
    await enterPostalCode(user);
    await user.click(screen.getByText("Finalizar compra"));

    expect(onCheckout).toHaveBeenCalledWith(
      expect.objectContaining({ nombre: "Cliente Demo", direccion: "Calle Falsa 123", ciudad: "CABA", codigoPostal: "1425" }),
      "DESC10",
      "tok_aprobado",
    );
  });

  it("sends the selected simulated payment method to onCheckout", async () => {
    const user = userEvent.setup();
    const onCheckout = vi.fn();

    render(
      <CartScreen
        items={[ITEM]}
        productImages={{}}
        authToken={null}
        onRemove={vi.fn()}
        onCheckout={onCheckout}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText("Nombre y apellido"), "Cliente Demo");
    await user.type(screen.getByLabelText("Dirección"), "Calle Falsa 123");
    await user.type(screen.getByLabelText("Ciudad"), "CABA");
    await enterPostalCode(user);
    await user.selectOptions(screen.getByLabelText("Medio de pago (simulado)"), "tok_rechazado");
    await user.click(screen.getByText("Finalizar compra"));

    expect(onCheckout).toHaveBeenCalledWith(expect.objectContaining({ nombre: "Cliente Demo" }), undefined, "tok_rechazado");
  });

  it("shows the daily roulette discount calculated by the server and subtracts it from the total", async () => {
    const user = userEvent.setup();
    mockedRoulette.mockResolvedValue({ percent: 10, amount: 130, categoryId: "alimentos", categoryName: "Alimentos" });

    render(
      <CartScreen
        items={[ITEM]}
        productImages={{}}
        authToken="tok"
        onRemove={vi.fn()}
        onCheckout={vi.fn()}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    await enterPostalCode(user);
    expect(await screen.findByText("Ruleta del día (Alimentos)")).toBeInTheDocument();
    expect(mockedRoulette).toHaveBeenCalledWith("tok");
    expect(screen.getByText(/-\s*\$\s*130,00/)).toBeInTheDocument();
    const subtotal = ITEM.price * ITEM.quantity;
    const expectedTotal = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(subtotal + 1500 - 130);
    const plain = (text: string) => text.replace(/ /g, " ");
    expect(screen.getByText((content) => plain(content) === plain(expectedTotal))).toBeInTheDocument();
  });

  it("shows the zone, the cost and the delivery time for the postal code", async () => {
    const user = userEvent.setup();
    mockedQuote.mockResolvedValue({ postalCode: 5000, zoneName: "Resto del país", cost: 2500, eta: "4 a 7 días hábiles", matchedZone: false });
    render(
      <CartScreen items={[ITEM]} productImages={{}} authToken={null} onRemove={vi.fn()} onCheckout={vi.fn()} onIncrement={vi.fn()} onDecrement={vi.fn()} />,
    );

    await user.type(screen.getByLabelText("Calculá el costo de tu envío"), "5000");

    expect(await screen.findByText(/llega en 4 a 7 días hábiles/)).toBeInTheDocument();
    expect(screen.getByText("resto del país")).toBeInTheDocument();
    expect(screen.getByText("$ 2.500,00")).toBeInTheDocument();
    expect(screen.getByText("$ 4.500,00")).toBeInTheDocument();
  });

  it("accepts the full postal code format and recalculates when it changes", async () => {
    const user = userEvent.setup();
    render(
      <CartScreen items={[ITEM]} productImages={{}} authToken={null} onRemove={vi.fn()} onCheckout={vi.fn()} onIncrement={vi.fn()} onDecrement={vi.fn()} />,
    );

    await user.type(screen.getByLabelText("Calculá el costo de tu envío"), "C1425ABC");

    await waitFor(() => expect(mockedQuote).toHaveBeenCalledWith("C1425ABC"));
  });

  it("asks to review a postal code that is not valid and does not calculate anything", async () => {
    const user = userEvent.setup();
    render(
      <CartScreen items={[ITEM]} productImages={{}} authToken={null} onRemove={vi.fn()} onCheckout={vi.fn()} onIncrement={vi.fn()} onDecrement={vi.fn()} />,
    );

    await user.type(screen.getByLabelText("Calculá el costo de tu envío"), "14x5");

    expect(await screen.findByText(/Revisá el código postal/)).toBeInTheDocument();
    expect(mockedQuote).not.toHaveBeenCalled();
    expect(screen.getByText("A calcular")).toBeInTheDocument();
  });

  it("shows the server error when the shipping cannot be calculated", async () => {
    const user = userEvent.setup();
    mockedQuote.mockRejectedValue(new Error("No pudimos calcular el envío."));
    render(
      <CartScreen items={[ITEM]} productImages={{}} authToken={null} onRemove={vi.fn()} onCheckout={vi.fn()} onIncrement={vi.fn()} onDecrement={vi.fn()} />,
    );

    await user.type(screen.getByLabelText("Calculá el costo de tu envío"), "1425");

    expect(await screen.findByText("No pudimos calcular el envío.")).toBeInTheDocument();
    expect(screen.getByText("A calcular")).toBeInTheDocument();
  });

  it("remembers the last postal code and calculates it right away", async () => {
    localStorage.setItem("petshop_postal_code", "1425");
    render(
      <CartScreen items={[ITEM]} productImages={{}} authToken={null} onRemove={vi.fn()} onCheckout={vi.fn()} onIncrement={vi.fn()} onDecrement={vi.fn()} />,
    );

    expect(screen.getByLabelText("Calculá el costo de tu envío")).toHaveValue("1425");
    expect(await screen.findByText(/llega en 24 a 48 hs/)).toBeInTheDocument();
    expect(screen.getByText("$ 3.500,00")).toBeInTheDocument();
  });

  it("shows Gratis as soon as the cart reaches the free shipping amount, even before the postal code", () => {
    const promos = { ...NO_CART_PROMOS, freeShippingEnabled: true, freeShippingThreshold: 2000 };
    render(
      <CartScreen items={[ITEM]} productImages={{}} authToken={null} promos={promos} onRemove={vi.fn()} onCheckout={vi.fn()} onIncrement={vi.fn()} onDecrement={vi.fn()} />,
    );

    expect(screen.getByText("Gratis")).toBeInTheDocument();
    expect(screen.queryByText("A calcular")).not.toBeInTheDocument();
    expect(screen.getByText(/Ingresá tu código postal para ver cuándo llega/)).toBeInTheDocument();
    expect(mockedQuote).not.toHaveBeenCalled();
  });
});
