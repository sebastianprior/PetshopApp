import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WishlistScreen } from "../../screens/WishlistScreen";
import type { Product } from "../../types";

const PRODUCTS: Product[] = [
  { id: "p1", name: "Croquetas", brand: "Marca", price: 100, rating: 4, categoryId: "alimentos", stock: 5 },
  { id: "p2", name: "Pelota", brand: "Marca", price: 50, rating: 4.2, categoryId: "juguetes", stock: 0 },
  { id: "p3", name: "Collar", brand: "Marca", price: 200, rating: 4.8, categoryId: "accesorios", stock: 3 },
];

const noop = () => {};

describe("WishlistScreen", () => {
  it("shows an empty state when the wishlist is empty", () => {
    render(<WishlistScreen products={PRODUCTS} wishlist={[]} onAddToCart={noop} onOpenProduct={noop} onToggleWishlist={noop} />);

    expect(screen.getByText("Todavía no agregaste productos a favoritos.")).toBeInTheDocument();
  });

  it("only renders the products that are in the wishlist", () => {
    render(
      <WishlistScreen
        products={PRODUCTS}
        wishlist={["p1", "p3"]}
        onAddToCart={noop}
        onOpenProduct={noop}
        onToggleWishlist={noop}
      />,
    );

    expect(screen.getByText("Croquetas")).toBeInTheDocument();
    expect(screen.getByText("Collar")).toBeInTheDocument();
    expect(screen.queryByText("Pelota")).not.toBeInTheDocument();
  });

  it("disables the add-to-cart button when the product has no stock", () => {
    render(
      <WishlistScreen products={PRODUCTS} wishlist={["p2"]} onAddToCart={noop} onOpenProduct={noop} onToggleWishlist={noop} />,
    );

    expect(screen.getByText("Sin stock")).toBeDisabled();
  });

  it("calls onToggleWishlist when removing a favorite", async () => {
    const user = userEvent.setup();
    const onToggleWishlist = vi.fn();

    render(
      <WishlistScreen
        products={PRODUCTS}
        wishlist={["p1"]}
        onAddToCart={noop}
        onOpenProduct={noop}
        onToggleWishlist={onToggleWishlist}
      />,
    );

    await user.click(screen.getByLabelText("Quitar de favoritos"));

    expect(onToggleWishlist).toHaveBeenCalledWith("p1");
  });

  it("calls onAddToCart with the right product", async () => {
    const user = userEvent.setup();
    const onAddToCart = vi.fn();

    render(
      <WishlistScreen
        products={PRODUCTS}
        wishlist={["p3"]}
        onAddToCart={onAddToCart}
        onOpenProduct={noop}
        onToggleWishlist={noop}
      />,
    );

    await user.click(screen.getByText("Agregar al carrito"));

    expect(onAddToCart).toHaveBeenCalledWith(PRODUCTS[2]);
  });
});
