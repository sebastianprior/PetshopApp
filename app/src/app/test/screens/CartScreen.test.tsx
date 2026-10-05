import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CartScreen } from "../../screens/CartScreen";
import type { CartItem } from "../../types";
import { validateCoupon } from "../../api";

vi.mock("../../api", () => ({
  validateCoupon: vi.fn(),
}));

const mockedValidateCoupon = vi.mocked(validateCoupon);

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
  });

  it("shows an empty state when there are no items", () => {
    render(
      <CartScreen
        items={[]}
        requireShipping={false}
        onRemove={vi.fn()}
        onCheckout={vi.fn()}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    expect(screen.getByText("Tu carrito está vacío.")).toBeInTheDocument();
  });

  it("computes subtotal, shipping and total for the given items", () => {
    render(
      <CartScreen
        items={[ITEM]}
        requireShipping={false}
        onRemove={vi.fn()}
        onCheckout={vi.fn()}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    // subtotal = 1000 * 2 = 2000, shipping = 1500, total = 3500
    expect(screen.getByText("$ 2.000,00")).toBeInTheDocument();
    expect(screen.getByText("$ 1.500,00")).toBeInTheDocument();
    expect(screen.getByText("$ 3.500,00")).toBeInTheDocument();
  });

  it("calls onIncrement, onDecrement and onRemove with the clicked item", async () => {
    const user = userEvent.setup();
    const onIncrement = vi.fn();
    const onDecrement = vi.fn();
    const onRemove = vi.fn();

    render(
      <CartScreen
        items={[ITEM]}
        requireShipping={false}
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
        requireShipping={false}
        onRemove={vi.fn()}
        onCheckout={vi.fn()}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

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
        requireShipping={false}
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
        requireShipping
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
    await user.click(screen.getByText("Finalizar compra"));

    expect(onCheckout).toHaveBeenCalledWith(
      expect.objectContaining({ nombre: "Cliente Demo", direccion: "Calle Falsa 123", ciudad: "CABA" }),
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
        requireShipping
        onRemove={vi.fn()}
        onCheckout={onCheckout}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText("Nombre y apellido"), "Cliente Demo");
    await user.type(screen.getByLabelText("Dirección"), "Calle Falsa 123");
    await user.type(screen.getByLabelText("Ciudad"), "CABA");
    await user.selectOptions(screen.getByLabelText("Medio de pago (simulado)"), "tok_rechazado");
    await user.click(screen.getByText("Finalizar compra"));

    expect(onCheckout).toHaveBeenCalledWith(expect.objectContaining({ nombre: "Cliente Demo" }), undefined, "tok_rechazado");
  });

  it("skips the shipping form and sends no shipping data when requireShipping is false", async () => {
    const user = userEvent.setup();
    const onCheckout = vi.fn();

    render(
      <CartScreen
        items={[ITEM]}
        requireShipping={false}
        onRemove={vi.fn()}
        onCheckout={onCheckout}
        onIncrement={vi.fn()}
        onDecrement={vi.fn()}
      />,
    );

    expect(screen.getByText("Iniciá sesión para guardar tu pedido y ver el historial.")).toBeInTheDocument();
    await user.click(screen.getByText("Finalizar compra"));

    expect(onCheckout).toHaveBeenCalledWith({}, undefined, undefined);
  });
});
