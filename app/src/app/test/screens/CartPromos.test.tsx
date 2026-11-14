import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CartScreen } from "../../screens/CartScreen";
import { CartPromosSettings } from "../../screens/CartPromosSettings";
import { fetchProducts, fetchShippingQuote, updateCartPromos } from "../../api";
import type { CartItem, CartPromos, Product } from "../../types";
import { NO_CART_PROMOS } from "../../types";

vi.mock("../../api", () => ({
  validateCoupon: vi.fn(),
  fetchRouletteCartDiscount: vi.fn().mockResolvedValue({ percent: 10, amount: 0, categoryId: null, categoryName: null }),
  fetchProducts: vi.fn(),
  fetchShippingQuote: vi.fn().mockResolvedValue({ postalCode: 1425, zoneName: "CABA", cost: 1500, eta: "24 a 48 hs", matchedZone: true }),
  updateCartPromos: vi.fn(),
}));

const mockedFetchProducts = vi.mocked(fetchProducts);
const mockedUpdate = vi.mocked(updateCartPromos);

const ITEM: CartItem = { productId: "p1", name: "Croquetas", variant: "alimentos", variantId: null, quantity: 2, price: 1000 };

const PROMOS: CartPromos = {
  freeShippingEnabled: true,
  freeShippingThreshold: 5000,
  giftEnabled: true,
  giftMinAmount: 8000,
  giftProductId: "g1",
  giftProductName: "Llavero",
  giftProductImageUrl: null,
  giftAvailable: true,
};

function renderCart(items: CartItem[], promos: CartPromos) {
  return render(
    <CartScreen
      items={items}
      productImages={{}}
      authToken={null}
      promos={promos}
      onRemove={vi.fn()}
      onCheckout={vi.fn()}
      onIncrement={vi.fn()}
      onDecrement={vi.fn()}
    />,
  );
}

describe("CartScreen: envío gratis y regalo", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("shows how much is missing for the free shipping and for the gift", async () => {
    const user = userEvent.setup();
    renderCart([ITEM], PROMOS); // subtotal 2000
    await user.type(screen.getByLabelText("Calculá el costo de tu envío"), "1425");
    await screen.findByText(/llega en 24 a 48 hs/);

    expect(screen.getByText(/Sumá \$\s*3\.000,00 más y el envío es gratis/)).toBeInTheDocument();
    expect(screen.getByText(/Sumá \$\s*6\.000,00 más y llevate de regalo Llavero/)).toBeInTheDocument();
    expect(screen.getByText("$ 1.500,00")).toBeInTheDocument(); // se sigue cobrando el envío
    expect(screen.queryByText("Gratis")).not.toBeInTheDocument();
  });

  it("makes the shipping free once the amount is reached, without the gift yet", () => {
    renderCart([{ ...ITEM, quantity: 6 }], PROMOS); // subtotal 6000

    expect(screen.getByText("¡Tenés envío gratis!")).toBeInTheDocument();
    expect(screen.getByText("Gratis")).toBeInTheDocument();
    expect(screen.getByText("$ 6.000,00", { selector: ".total-row strong" })).toBeInTheDocument();
    expect(screen.queryByText("Regalo")).not.toBeInTheDocument();
  });

  it("adds the gift line at no cost when the gift amount is reached", () => {
    renderCart([{ ...ITEM, quantity: 9 }], PROMOS); // subtotal 9000

    expect(screen.getByText("¡Te llevás de regalo Llavero!")).toBeInTheDocument();
    expect(screen.getByText("Regalo")).toBeInTheDocument();
    expect(screen.getAllByText("Gratis")).toHaveLength(2); // línea del regalo y envío
    expect(screen.getByText("$ 9.000,00", { selector: ".total-row strong" })).toBeInTheDocument();
  });

  it("does not offer a gift that is not available (no stock)", () => {
    renderCart([{ ...ITEM, quantity: 9 }], { ...PROMOS, giftAvailable: false });

    expect(screen.queryByText(/de regalo/)).not.toBeInTheDocument();
    expect(screen.queryByText("Regalo")).not.toBeInTheDocument();
  });

  it("shows no bars when nothing is configured", async () => {
    const user = userEvent.setup();
    renderCart([ITEM], NO_CART_PROMOS);
    await user.type(screen.getByLabelText("Calculá el costo de tu envío"), "1425");

    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(await screen.findByText("$ 1.500,00")).toBeInTheDocument();
  });
});

describe("CartPromosSettings", () => {
  const PRODUCTS: Product[] = [
    { id: "g1", name: "Llavero", brand: "M", price: 300, rating: 4, categoryId: "accesorios", stock: 5 },
    { id: "g2", name: "Agotado", brand: "M", price: 300, rating: 4, categoryId: "accesorios", stock: 0 },
    { id: "g3", name: "Con talles", brand: "M", price: 300, rating: 4, categoryId: "accesorios", stock: 9, hasVariants: true },
  ];

  beforeEach(() => {
    mockedFetchProducts.mockReset().mockResolvedValue(PRODUCTS);
    mockedUpdate.mockReset();
  });

  it("only offers products with stock and without variants as the gift", async () => {
    const user = userEvent.setup();
    render(<CartPromosSettings authToken="tok" promos={NO_CART_PROMOS} onSaved={vi.fn()} />);

    await user.click(screen.getByLabelText("Producto de regalo a partir de un monto"));
    await waitFor(() => expect(screen.getByRole("option", { name: /Llavero/ })).toBeInTheDocument());

    expect(screen.queryByRole("option", { name: /Agotado/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Con talles/ })).not.toBeInTheDocument();
  });

  it("saves the free shipping and the gift and reports them to the parent", async () => {
    const user = userEvent.setup();
    const onSaved = vi.fn();
    mockedUpdate.mockResolvedValue(PROMOS);
    render(<CartPromosSettings authToken="tok" promos={NO_CART_PROMOS} onSaved={onSaved} />);

    await user.click(screen.getByLabelText("Envío gratis a partir de un monto"));
    await user.type(screen.getByLabelText(/Monto mínimo para el envío gratis/), "5000");
    await user.click(screen.getByLabelText("Producto de regalo a partir de un monto"));
    await user.type(screen.getByLabelText(/Monto mínimo para el regalo/), "8000");
    await waitFor(() => expect(screen.getByRole("option", { name: /Llavero/ })).toBeInTheDocument());
    await user.selectOptions(screen.getByLabelText("Producto de regalo"), "g1");
    await user.click(screen.getByText("Guardar cambios"));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith("tok", {
        freeShippingEnabled: true,
        freeShippingThreshold: 5000,
        giftEnabled: true,
        giftMinAmount: 8000,
        giftProductId: "g1",
      }),
    );
    expect(onSaved).toHaveBeenCalledWith(PROMOS);
    expect(await screen.findByText(/Cambios guardados/)).toBeInTheDocument();
  });

  it("shows the server error when the settings are rejected", async () => {
    const user = userEvent.setup();
    mockedUpdate.mockRejectedValue(new Error("Elegí el producto de regalo."));
    render(<CartPromosSettings authToken="tok" promos={NO_CART_PROMOS} onSaved={vi.fn()} />);

    await user.click(screen.getByLabelText("Producto de regalo a partir de un monto"));
    await user.click(screen.getByText("Guardar cambios"));

    expect(await screen.findByText("Elegí el producto de regalo.")).toBeInTheDocument();
  });
});
