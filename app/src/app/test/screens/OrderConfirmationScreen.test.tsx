import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OrderConfirmationScreen } from "../../screens/OrderConfirmationScreen";
import type { CheckoutResult } from "../../types";

const BASE: CheckoutResult = {
  ok: true,
  items: [{ productId: "p1", name: "Alimento", variant: "1kg", quantity: 1, price: 1000 }],
  subtotal: 1000,
  shippingCost: 1500,
  discountAmount: 0,
  couponCode: null,
  total: 2500,
  orderId: 9,
};

describe("OrderConfirmationScreen", () => {
  it("shows the confirmation message for an approved sale", () => {
    render(<OrderConfirmationScreen result={{ ...BASE, estado: "COMPLETADA" }} isLoggedIn onNavigate={vi.fn()} />);

    expect(screen.getByText("¡Compra confirmada!")).toBeInTheDocument();
  });

  it("shows the 'processing payment' message when the sale is PAGO_PENDIENTE", () => {
    render(<OrderConfirmationScreen result={{ ...BASE, estado: "PAGO_PENDIENTE" }} isLoggedIn onNavigate={vi.fn()} />);

    expect(screen.getByText("Estamos procesando tu pago")).toBeInTheDocument();
    expect(screen.queryByText("¡Compra confirmada!")).not.toBeInTheDocument();
  });
});
