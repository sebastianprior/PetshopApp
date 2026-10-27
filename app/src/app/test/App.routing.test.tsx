import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, configure, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../App";
import * as api from "../api";
import type { Product } from "../types";

vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return {
    ...actual,
    fetchCategories: vi.fn(),
    fetchProducts: vi.fn(),
    fetchProduct: vi.fn(),
    fetchCart: vi.fn(),
    fetchRoulette: vi.fn(),
    fetchSiteSettings: vi.fn(),
    fetchCartPromos: vi.fn(),
    fetchFaq: vi.fn(),
    fetchWishlist: vi.fn(),
    fetchProductReviews: vi.fn(),
    fetchProductVariants: vi.fn(),
  };
});

const COLLAR: Product = { id: "p1", name: "Collar Comfort", brand: "WalkEasy", price: 899, rating: 4.5, categoryId: "accesorios", stock: 10 };
const PELOTA: Product = { id: "p9", name: "Pelota Squeaky", brand: "PetPlay", price: 349, rating: 4.3, categoryId: "juguetes", stock: 10 };

const at = (path: string) => window.history.replaceState(null, "", path);

// La tienda espera 300 ms antes de pedir el catálogo: con la máquina cargada, 1 s de espera es justo
configure({ asyncUtilTimeout: 4000 });

describe("App: rutas", () => {
  beforeEach(() => {
    localStorage.clear();
    window.scrollTo = vi.fn();
    vi.mocked(api.fetchCategories).mockResolvedValue([
      { id: "accesorios", name: "Accesorios" },
      { id: "juguetes", name: "Juguetes" },
    ]);
    vi.mocked(api.fetchProducts).mockResolvedValue([COLLAR, PELOTA]);
    vi.mocked(api.fetchProduct).mockRejectedValue(new Error("404"));
    vi.mocked(api.fetchCart).mockResolvedValue([]);
    vi.mocked(api.fetchRoulette).mockResolvedValue(null as never);
    vi.mocked(api.fetchSiteSettings).mockResolvedValue({ topbarItems: [], announcementMode: "OFF", announcementText: "" });
    vi.mocked(api.fetchCartPromos).mockResolvedValue({
      freeShippingEnabled: false,
      freeShippingThreshold: 0,
      giftEnabled: false,
      giftMinAmount: 0,
      giftProductId: null,
      giftAvailable: false,
    });
    vi.mocked(api.fetchFaq).mockResolvedValue([{ id: 1, question: "¿Cuánto tarda mi pedido?", answer: "24 hs." }]);
    vi.mocked(api.fetchWishlist).mockResolvedValue([]);
    vi.mocked(api.fetchProductReviews).mockResolvedValue([]);
    vi.mocked(api.fetchProductVariants).mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("opens a product directly from its link", async () => {
    at("/productos/p1");
    render(<App />);

    expect(await screen.findByRole("heading", { level: 1, name: "Collar Comfort" })).toBeInTheDocument();
    expect(document.title).toBe("Collar Comfort · Petshop");
  });

  it("fetches a product that is not in the loaded catalog (shared link)", async () => {
    vi.mocked(api.fetchProduct).mockResolvedValue({ ...PELOTA, id: "p77", name: "Arnés Trail" });
    at("/productos/p77");
    render(<App />);

    expect(await screen.findByRole("heading", { level: 1, name: "Arnés Trail" })).toBeInTheDocument();
    expect(api.fetchProduct).toHaveBeenCalledWith("p77");
  });

  it("says the product was not found for a link to a product that does not exist", async () => {
    at("/productos/no-existe");
    render(<App />);

    expect(await screen.findByText("No se encontró el producto.")).toBeInTheDocument();
  });

  it("shows the home for an address that does not exist and fixes the address", async () => {
    at("/ruta-inexistente");
    render(<App />);

    expect(await screen.findByRole("heading", { level: 1, name: /Todo para tu mascota/ })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/");
  });

  it("sends a customer who is not logged in to the login and remembers where they wanted to go", async () => {
    at("/mis-pedidos");
    render(<App />);

    expect(await screen.findByRole("heading", { name: "Ingresar" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/ingresar");
    expect(window.location.search).toBe("?next=%2Fmis-pedidos");
  });

  it("does not show the order confirmation without a purchase", async () => {
    at("/compra-confirmada");
    render(<App />);

    await waitFor(() => expect(window.location.pathname).toBe("/"));
  });

  it("opens the catalog already filtered by the category and the search in the address", async () => {
    at("/productos?categoria=juguetes&q=pelota");
    render(<App />);

    await waitFor(() => expect(api.fetchProducts).toHaveBeenCalledWith("juguetes", "", "pelota"));
    expect(await screen.findByRole("heading", { level: 1, name: "Juguetes" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Juguetes/, pressed: true })).toBeInTheDocument();
  });

  it("puts the category in the address when a tile of the home is clicked, and the back button returns to the home", async () => {
    const user = userEvent.setup();
    at("/");
    render(<App />);

    await user.click(await screen.findByRole("button", { name: /Juguetes/ }));

    expect(window.location.pathname + window.location.search).toBe("/productos?categoria=juguetes");
    expect(await screen.findByRole("heading", { level: 1, name: "Juguetes" })).toBeInTheDocument();

    act(() => window.history.back());

    expect(await screen.findByRole("heading", { level: 1, name: /Todo para tu mascota/ })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/");
  });

  it("updates the address while the customer filters the catalog without adding history entries", async () => {
    const user = userEvent.setup();
    at("/productos");
    render(<App />);
    await screen.findByRole("heading", { level: 1, name: "Todos los productos" });
    const history = window.history.length;

    await user.click(screen.getByRole("button", { name: /Juguetes/ }));
    expect(window.location.search).toBe("?categoria=juguetes");

    await user.type(screen.getByPlaceholderText("Nombre o marca..."), "pel");
    expect(window.location.search).toBe("?categoria=juguetes&q=pel");
    expect(window.history.length).toBe(history);
  });

  it("opens the frequently asked questions at its own address from the footer", async () => {
    const user = userEvent.setup();
    at("/");
    render(<App />);

    await user.click(await screen.findByRole("button", { name: "Preguntas frecuentes", hidden: true }));

    expect(window.location.pathname).toBe("/preguntas-frecuentes");
    expect(await screen.findByRole("heading", { level: 1, name: "Preguntas frecuentes" })).toBeInTheDocument();
    expect(document.title).toBe("Preguntas frecuentes · Petshop");
  });
});
