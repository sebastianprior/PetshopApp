import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { discountOf, isOffer } from "../../components/offers";
import { HomeScreen } from "../../screens/HomeScreen";
import type { Product } from "../../types";

const base: Product = { id: "p", name: "Producto", brand: "M", price: 1000, rating: 4, categoryId: "alimentos", stock: 5 };
const noop = () => {};

describe("offers", () => {
  it("measures the real discount from the promotional price or the crossed-out price", () => {
    expect(discountOf({ ...base, precioPromocional: 800 })).toBe(20);
    expect(discountOf({ ...base, oldPrice: 1250 })).toBe(20);
    expect(discountOf({ ...base, oldPrice: 900 })).toBe(0);
    expect(discountOf(base)).toBe(0);
  });

  it("counts as an offer when it has a real discount or an offer badge", () => {
    expect(isOffer({ ...base, oldPrice: 1250 })).toBe(true);
    expect(isOffer({ ...base, precioPromocional: 900 })).toBe(true);
    expect(isOffer({ ...base, badge: "Oferta" })).toBe(true);
    expect(isOffer({ ...base, badge: "Nuevo" })).toBe(false);
    expect(isOffer(base)).toBe(false);
  });

  it("shows on the home the 4 offers with the biggest discount, not just the promotional-price ones", () => {
    const products: Product[] = [
      { ...base, id: "a", name: "Sin oferta" },
      { ...base, id: "b", name: "Descuento 10", oldPrice: 1111 },
      { ...base, id: "c", name: "Descuento 30", oldPrice: 1429 },
      { ...base, id: "d", name: "Promo 20", precioPromocional: 800, tipoPromocion: "20% OFF" },
      { ...base, id: "e", name: "Descuento 5", oldPrice: 1053 },
      { ...base, id: "f", name: "Descuento 40", oldPrice: 1667 },
    ];

    render(
      <HomeScreen
        categories={[]}
        products={products}
        currentUser={null}
        onNavigate={noop}
        onAddToCart={noop}
        onOpenProduct={noop}
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    const section = screen.getByRole("heading", { name: "Ofertas de la semana" }).closest("section") as HTMLElement;
    const names = Array.from(section.querySelectorAll("h3")).map((h) => h.textContent);
    expect(names).toEqual(["Descuento 40", "Descuento 30", "Promo 20", "Descuento 10"]);
  });
});
