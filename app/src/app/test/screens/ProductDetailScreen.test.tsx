import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProductDetailScreen } from "../../screens/ProductDetailScreen";
import type { Product, ProductVariant, Review } from "../../types";
import { deleteReview, fetchProductReviews, fetchProductVariants, submitReview } from "../../api";

vi.mock("../../api", () => ({
  deleteReview: vi.fn(),
  fetchProductReviews: vi.fn(),
  fetchProductVariants: vi.fn(),
  submitReview: vi.fn(),
}));

const mockedFetchReviews = vi.mocked(fetchProductReviews);
const mockedFetchVariants = vi.mocked(fetchProductVariants);
const mockedSubmitReview = vi.mocked(submitReview);
const mockedDeleteReview = vi.mocked(deleteReview);

const PRODUCT: Product = {
  id: "p1",
  name: "Collar Comfort L",
  brand: "WalkEasy",
  price: 899,
  rating: 4.5,
  categoryId: "accesorios",
  stock: 25,
  hasVariants: false,
  imageUrl: "https://example.com/product.jpg",
};

const VARIANT_WITH_IMAGE: ProductVariant = {
  id: 1,
  productId: "p1",
  talle: "M",
  color: "Negro",
  stock: 5,
  imageUrl: "https://example.com/variant-m.jpg",
};

const VARIANT_NO_IMAGE: ProductVariant = {
  id: 2,
  productId: "p1",
  talle: "S",
  color: "Blanco",
  stock: 0,
  imageUrl: null,
};

const noop = () => {};

describe("ProductDetailScreen", () => {
  beforeEach(() => {
    mockedFetchReviews.mockReset().mockResolvedValue([]);
    mockedFetchVariants.mockReset().mockResolvedValue([]);
    mockedSubmitReview.mockReset();
    mockedDeleteReview.mockReset();
  });

  it("shows an empty state when there is no product", () => {
    render(
      <ProductDetailScreen
        product={null}
        authToken={null}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
        onBack={noop}
        onAddToCart={noop}
      />,
    );

    expect(screen.getByText("No se encontró el producto.")).toBeInTheDocument();
  });

  it("lets a customer add a simple product (no variants) to the cart", async () => {
    const user = userEvent.setup();
    const onAddToCart = vi.fn();

    render(
      <ProductDetailScreen
        product={PRODUCT}
        authToken={null}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
        onBack={noop}
        onAddToCart={onAddToCart}
      />,
    );

    await waitFor(() => expect(mockedFetchReviews).toHaveBeenCalledWith("p1"));
    const addButton = screen.getByText("Agregar al carrito");
    expect(addButton).toBeEnabled();

    await user.click(addButton);

    expect(onAddToCart).toHaveBeenCalledWith(PRODUCT, undefined, 1);
  });

  it("adds the chosen quantity and never lets it exceed the stock", async () => {
    const user = userEvent.setup();
    const onAddToCart = vi.fn();
    render(
      <ProductDetailScreen
        product={{ ...PRODUCT, stock: 3 }}
        authToken={null}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
        onBack={noop}
        onAddToCart={onAddToCart}
      />,
    );

    expect(screen.getByLabelText("Menos unidades")).toBeDisabled();
    await user.click(screen.getByLabelText("Más unidades"));
    await user.click(screen.getByLabelText("Más unidades"));
    expect(screen.getByLabelText("Más unidades")).toBeDisabled();

    await user.click(screen.getByText("Agregar al carrito"));

    expect(onAddToCart).toHaveBeenCalledWith(expect.objectContaining({ id: "p1" }), undefined, 3);
  });

  it("requires picking a variant before enabling add-to-cart, and swaps the image", async () => {
    const user = userEvent.setup();
    const onAddToCart = vi.fn();
    mockedFetchVariants.mockResolvedValue([VARIANT_WITH_IMAGE, VARIANT_NO_IMAGE]);
    const productWithVariants = { ...PRODUCT, hasVariants: true };

    render(
      <ProductDetailScreen
        product={productWithVariants}
        authToken={null}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
        onBack={noop}
        onAddToCart={onAddToCart}
      />,
    );

    await waitFor(() => expect(screen.getByText("Talle M / Negro")).toBeInTheDocument());

    expect(screen.getByText("Elegí una opción", { selector: "button" })).toBeDisabled();

    const mainImage = screen.getByAltText("Collar Comfort L") as HTMLImageElement;
    expect(mainImage.src).toBe("https://example.com/product.jpg");

    await user.click(screen.getByText("Talle M / Negro"));

    expect(mainImage.src).toBe("https://example.com/variant-m.jpg");
    const addButton = screen.getByText("Agregar al carrito");
    expect(addButton).toBeEnabled();

    await user.click(addButton);
    expect(onAddToCart).toHaveBeenCalledWith(productWithVariants, VARIANT_WITH_IMAGE, 1);

    // the out-of-stock variant cannot be selected
    expect(screen.getByText("Talle S / Blanco").closest("button")).toBeDisabled();
  });

  it("hides the buy box and wishlist heart, and shows delete review links, for admins", async () => {
    mockedFetchReviews.mockResolvedValue([
      { id: 1, productId: "p1", authorName: "Cliente", rating: 5, comment: "Buenísimo", createdAt: "2026-01-01T00:00:00Z" } as Review,
    ]);

    render(
      <ProductDetailScreen
        product={PRODUCT}
        authToken="admin-token"
        isAdmin
        wishlist={[]}
        onToggleWishlist={noop}
        onBack={noop}
        onAddToCart={noop}
      />,
    );

    await waitFor(() => expect(screen.getByText("Buenísimo")).toBeInTheDocument());

    expect(screen.queryByText("Agregar al carrito")).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/favoritos/i)).not.toBeInTheDocument();
    expect(screen.getByText("Eliminar")).toBeInTheDocument();
  });

  it("toggles the wishlist heart for a logged-in customer", async () => {
    const user = userEvent.setup();
    const onToggleWishlist = vi.fn();

    render(
      <ProductDetailScreen
        product={PRODUCT}
        authToken={null}
        isAdmin={false}
        wishlist={["p1"]}
        onToggleWishlist={onToggleWishlist}
        onBack={noop}
        onAddToCart={noop}
      />,
    );

    const heart = screen.getByLabelText("Quitar de favoritos");
    await user.click(heart);

    expect(onToggleWishlist).toHaveBeenCalledWith("p1");
  });

  it("shows a login hint instead of the review form when logged out", async () => {
    render(
      <ProductDetailScreen
        product={PRODUCT}
        authToken={null}
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
        onBack={noop}
        onAddToCart={noop}
      />,
    );

    await waitFor(() => expect(mockedFetchReviews).toHaveBeenCalled());
    expect(screen.getByText("Iniciá sesión para dejar tu reseña.")).toBeInTheDocument();
  });

  it("validates the comment before submitting a review, then submits it", async () => {
    const user = userEvent.setup();
    mockedSubmitReview.mockResolvedValue({
      id: 9,
      productId: "p1",
      authorName: "Cliente Demo",
      rating: 5,
      comment: "Excelente producto",
      createdAt: "2026-01-01T00:00:00Z",
    });

    render(
      <ProductDetailScreen
        product={PRODUCT}
        authToken="user-token"
        isAdmin={false}
        wishlist={[]}
        onToggleWishlist={noop}
        onBack={noop}
        onAddToCart={noop}
      />,
    );

    await waitFor(() => expect(mockedFetchReviews).toHaveBeenCalled());

    await user.click(screen.getByText("Publicar reseña"));
    expect(screen.getByText("Contanos algo sobre tu experiencia con el producto.")).toBeInTheDocument();
    expect(mockedSubmitReview).not.toHaveBeenCalled();

    await user.type(screen.getByPlaceholderText("Contanos tu experiencia con el producto"), "Excelente producto");
    await user.click(screen.getByText("Publicar reseña"));

    await waitFor(() => expect(screen.getByText("Excelente producto")).toBeInTheDocument());
    expect(mockedSubmitReview).toHaveBeenCalledWith("user-token", "p1", 5, "Excelente producto");
  });

  it("lets an admin delete a review", async () => {
    const user = userEvent.setup();
    mockedFetchReviews.mockResolvedValue([
      { id: 1, productId: "p1", authorName: "Cliente", rating: 3, comment: "Regular", createdAt: "2026-01-01T00:00:00Z" } as Review,
    ]);
    mockedDeleteReview.mockResolvedValue({ ok: true });

    render(
      <ProductDetailScreen
        product={PRODUCT}
        authToken="admin-token"
        isAdmin
        wishlist={[]}
        onToggleWishlist={noop}
        onBack={noop}
        onAddToCart={noop}
      />,
    );

    await waitFor(() => expect(screen.getByText("Regular")).toBeInTheDocument());

    await user.click(screen.getByText("Eliminar"));

    await waitFor(() => expect(mockedDeleteReview).toHaveBeenCalledWith("admin-token", "p1", 1));
    await waitFor(() => expect(screen.queryByText("Regular")).not.toBeInTheDocument());
  });
});
