import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProductCard, ProductGridSkeleton } from "../../components/ProductCard";
import type { Product } from "../../types";

const BASE: Product = {
  id: "p1",
  name: "Croquetas",
  brand: "SuperDog",
  price: 1000,
  rating: 4.5,
  categoryId: "alimentos",
  stock: 20,
};

const noop = () => {};

function renderCard(product: Product, overrides: Partial<Parameters<typeof ProductCard>[0]> = {}) {
  return render(
    <ProductCard
      product={product}
      isAdmin={false}
      wishlist={[]}
      onToggleWishlist={noop}
      onAddToCart={noop}
      onOpenProduct={noop}
      {...overrides}
    />,
  );
}

describe("ProductCard", () => {
  it("charges and shows the promotional price, with the regular price crossed out and the discount", () => {
    renderCard({ ...BASE, precioPromocional: 800, tipoPromocion: "20% OFF" });

    expect(screen.getByText("$ 800,00")).toBeInTheDocument();
    expect(screen.getByText("$ 1.000,00")).toBeInTheDocument();
    expect(screen.getByText("-20%")).toBeInTheDocument();
    expect(screen.getByText("20% OFF")).toBeInTheDocument();
  });

  it("shows the old price and discount when there is no promotion but a higher previous price", () => {
    renderCard({ ...BASE, oldPrice: 1250 });

    expect(screen.getByText("$ 1.000,00")).toBeInTheDocument();
    expect(screen.getByText("$ 1.250,00")).toBeInTheDocument();
    expect(screen.getByText("-20%")).toBeInTheDocument();
  });

  it("does not show a discount when the previous price is not higher", () => {
    renderCard({ ...BASE, oldPrice: 900 });

    expect(screen.queryByText(/^-\d+%$/)).not.toBeInTheDocument();
  });

  it("disables the buy button and says so when there is no stock", () => {
    renderCard({ ...BASE, stock: 0 });

    expect(screen.getByText("Sin stock")).toBeDisabled();
    expect(screen.getByText("Agotado")).toBeInTheDocument();
  });

  it("warns when only a few units are left", () => {
    renderCard({ ...BASE, stock: 3 });

    expect(screen.getByText("¡Últimas 3!")).toBeInTheDocument();
  });

  it("offers options instead of adding straight away when the product has variants", async () => {
    const user = userEvent.setup();
    const onAddToCart = vi.fn();
    renderCard({ ...BASE, hasVariants: true }, { onAddToCart });

    await user.click(screen.getByText("Ver opciones"));

    expect(onAddToCart).toHaveBeenCalledWith(expect.objectContaining({ id: "p1" }));
  });

  it("shows the extra tag (e.g. the daily roulette discount)", () => {
    renderCard(BASE, { extraTag: "-10% hoy" });

    expect(screen.getByText("-10% hoy")).toBeInTheDocument();
  });

  it("marks the heart as pressed when the product is a favorite", () => {
    renderCard(BASE, { wishlist: ["p1"] });

    expect(screen.getByRole("button", { name: "Quitar de favoritos" })).toHaveAttribute("aria-pressed", "true");
  });
});

describe("ProductGridSkeleton", () => {
  it("announces the loading state to assistive technology", () => {
    render(<ProductGridSkeleton count={3} />);

    expect(screen.getByText("Cargando productos...")).toBeInTheDocument();
    expect(document.querySelectorAll(".skeleton-card")).toHaveLength(3);
  });
});
