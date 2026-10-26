import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProductsScreen } from "../../screens/ProductsScreen";
import type { Product } from "../../types";

function makeProducts(count: number): Product[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `p${i + 1}`,
    name: `Producto ${i + 1}`,
    brand: "Marca",
    price: 100 * (i + 1),
    rating: 4.5,
    categoryId: "alimentos",
    stock: 10,
  }));
}

const noop = () => {};

describe("ProductsScreen", () => {
  it("shows the loading state instead of the grid", () => {
    render(
      <ProductsScreen
        products={[]}
        loading
        categoryFilter="all"
        sort=""
        search=""
        onCategoryChange={noop}
        onSortChange={noop}
        onSearchChange={noop}
        onAddToCart={noop}
        onOpenProduct={noop}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    expect(screen.getByText("Cargando productos...")).toBeInTheDocument();
  });

  it("shows an empty state when there are no products", () => {
    render(
      <ProductsScreen
        products={[]}
        loading={false}
        categoryFilter="all"
        sort=""
        search=""
        onCategoryChange={noop}
        onSortChange={noop}
        onSearchChange={noop}
        onAddToCart={noop}
        onOpenProduct={noop}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    expect(screen.getByText("No encontramos productos con esos filtros.")).toBeInTheDocument();
  });

  it("paginates at 12 products per page", async () => {
    const user = userEvent.setup();
    const products = makeProducts(25);

    render(
      <ProductsScreen
        products={products}
        loading={false}
        categoryFilter="all"
        sort=""
        search=""
        onCategoryChange={noop}
        onSortChange={noop}
        onSearchChange={noop}
        onAddToCart={noop}
        onOpenProduct={noop}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    expect(screen.getAllByRole("article")).toHaveLength(12);
    expect(screen.getByText("Página 1 de 3")).toBeInTheDocument();
    expect(screen.getByText("← Anterior")).toBeDisabled();

    await user.click(screen.getByText("Siguiente →"));

    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument();
    expect(screen.getByText("Producto 13")).toBeInTheDocument();
    expect(screen.queryByText("Producto 1")).not.toBeInTheDocument();

    await user.click(screen.getByText("Siguiente →"));
    expect(screen.getByText("Página 3 de 3")).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(1);
    expect(screen.getByText("Siguiente →")).toBeDisabled();
  });

  it("does not show pagination controls when everything fits on one page", () => {
    render(
      <ProductsScreen
        products={makeProducts(5)}
        loading={false}
        categoryFilter="all"
        sort=""
        search=""
        onCategoryChange={noop}
        onSortChange={noop}
        onSearchChange={noop}
        onAddToCart={noop}
        onOpenProduct={noop}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    expect(screen.queryByText(/Página/)).not.toBeInTheDocument();
  });

  it("hides purchase and wishlist controls for admins", () => {
    render(
      <ProductsScreen
        products={makeProducts(2)}
        loading={false}
        categoryFilter="all"
        sort=""
        search=""
        onCategoryChange={noop}
        onSortChange={noop}
        onSearchChange={noop}
        onAddToCart={noop}
        onOpenProduct={noop}
        isAdmin
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    expect(screen.queryByText("Agregar al carrito")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Agregar a favoritos")).not.toBeInTheDocument();
  });

  it("toggles wishlist without triggering onOpenProduct, and reflects active state", async () => {
    const user = userEvent.setup();
    const onToggleWishlist = vi.fn();
    const onOpenProduct = vi.fn();
    const products = makeProducts(2);

    render(
      <ProductsScreen
        products={products}
        loading={false}
        categoryFilter="all"
        sort=""
        search=""
        onCategoryChange={noop}
        onSortChange={noop}
        onSearchChange={noop}
        onAddToCart={noop}
        onOpenProduct={onOpenProduct}
        isAdmin={false}
        wishlist={["p1"]}
        onToggleWishlist={onToggleWishlist}
      />,
    );

    const hearts = screen.getAllByRole("button", { name: /favoritos/i });
    expect(hearts[0]).toHaveAccessibleName("Quitar de favoritos");
    expect(hearts[1]).toHaveAccessibleName("Agregar a favoritos");

    await user.click(hearts[1]);

    expect(onToggleWishlist).toHaveBeenCalledWith("p2");
    expect(onOpenProduct).not.toHaveBeenCalled();
  });

  it("forwards search, category and sort changes", async () => {
    const user = userEvent.setup();
    const onSearchChange = vi.fn();
    const onCategoryChange = vi.fn();
    const onSortChange = vi.fn();

    render(
      <ProductsScreen
        products={[]}
        loading={false}
        categoryFilter="all"
        sort=""
        search=""
        onCategoryChange={onCategoryChange}
        onSortChange={onSortChange}
        onSearchChange={onSearchChange}
        onAddToCart={noop}
        onOpenProduct={noop}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    await user.type(screen.getByPlaceholderText("Nombre o marca..."), "a");
    expect(onSearchChange).toHaveBeenCalledWith("a");

    await user.click(screen.getByRole("button", { name: /Juguetes/ }));
    expect(onCategoryChange).toHaveBeenCalledWith("juguetes");

    await user.click(screen.getByRole("button", { name: /Todas/ }));
    expect(onCategoryChange).toHaveBeenCalledWith("all");

    await user.selectOptions(screen.getByDisplayValue("Relevancia"), "menorprecio");
    expect(onSortChange).toHaveBeenCalledWith("menorprecio");
  });
  it("builds the category chips from the server categories and marks the active one", async () => {
    const user = userEvent.setup();
    const onCategoryChange = vi.fn();

    render(
      <ProductsScreen
        products={makeProducts(2)}
        loading={false}
        categories={[
          { id: "higiene", name: "Higiene" },
          { id: "salud", name: "Salud" },
        ]}
        categoryFilter="salud"
        sort=""
        search=""
        onCategoryChange={onCategoryChange}
        onSortChange={noop}
        onSearchChange={noop}
        onAddToCart={noop}
        onOpenProduct={noop}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    expect(screen.getByRole("button", { name: /Salud/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Todas/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("heading", { level: 1, name: "Salud" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Juguetes/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Higiene/ }));
    expect(onCategoryChange).toHaveBeenCalledWith("higiene");
  });

  it("offers to clear the filters when nothing matches", async () => {
    const user = userEvent.setup();
    const onCategoryChange = vi.fn();
    const onSearchChange = vi.fn();

    render(
      <ProductsScreen
        products={[]}
        loading={false}
        categoryFilter="gatos"
        sort=""
        search="zzz"
        onCategoryChange={onCategoryChange}
        onSortChange={noop}
        onSearchChange={onSearchChange}
        onAddToCart={noop}
        onOpenProduct={noop}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
      />,
    );

    await user.click(screen.getByText("Limpiar filtros"));

    expect(onSearchChange).toHaveBeenCalledWith("");
    expect(onCategoryChange).toHaveBeenCalledWith("all");
  });
});
