import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SalchiBot } from "../../screens/SalchiBot";
import { askSalchiBot } from "../../api";
import type { Product } from "../../types";

vi.mock("../../api", () => ({ askSalchiBot: vi.fn() }));
const mockedAsk = vi.mocked(askSalchiBot);

const CORREA: Product = {
  id: "p33",
  name: "Correa reflectante 1.5m",
  brand: "NightWalk",
  price: 1299,
  oldPrice: 1699,
  rating: 4.5,
  imageUrl: "/products/p33.svg",
  categoryId: "accesorios",
  stock: 10,
};

const WAIT = { timeout: 3000 };

function setup() {
  const onOpenProduct = vi.fn();
  const onAddToCart = vi.fn();
  render(<SalchiBot onOpenProduct={onOpenProduct} onAddToCart={onAddToCart} />);
  return { onOpenProduct, onAddToCart };
}

describe("SalchiBot", () => {
  beforeEach(() => {
    mockedAsk.mockReset();
  });

  it("shows only the floating button until it is opened, then greets with suggestions", async () => {
    const user = userEvent.setup();
    setup();

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Abrir chat con Salchi Bot" }));

    expect(screen.getByRole("dialog", { name: "Chat con Salchi Bot" })).toBeInTheDocument();
    expect(screen.getByText(/Soy Salchi Bot/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Algo para el paseo" })).toBeInTheDocument();
  });

  it("sends what the customer types and shows the recommended products", async () => {
    const user = userEvent.setup();
    mockedAsk.mockResolvedValue({ reply: "¡Guau! Para los paseos te recomiendo:", products: [CORREA], suggestions: ["Algo más barato"] });
    setup();
    await user.click(screen.getByRole("button", { name: "Abrir chat con Salchi Bot" }));

    await user.type(screen.getByLabelText("Mensaje para Salchi Bot"), "quiero algo para pasear");
    await user.click(screen.getByRole("button", { name: "Enviar mensaje" }));

    expect(screen.getByText("quiero algo para pasear")).toBeInTheDocument();
    expect(await screen.findByText("¡Guau! Para los paseos te recomiendo:", {}, WAIT)).toBeInTheDocument();
    expect(mockedAsk).toHaveBeenCalledWith("quiero algo para pasear", []);
    expect(screen.getByText("Correa reflectante 1.5m")).toBeInTheDocument();
    expect(screen.getByAltText("Correa reflectante 1.5m")).toHaveAttribute("src", "/products/p33.svg");
    expect(screen.getByRole("button", { name: "Algo más barato" })).toBeInTheDocument();
  });

  it("remembers the previous customer messages as history", async () => {
    const user = userEvent.setup();
    mockedAsk.mockResolvedValue({ reply: "Listo", products: [], suggestions: [] });
    setup();
    await user.click(screen.getByRole("button", { name: "Abrir chat con Salchi Bot" }));

    await user.type(screen.getByLabelText("Mensaje para Salchi Bot"), "tengo un gato");
    await user.click(screen.getByRole("button", { name: "Enviar mensaje" }));
    await waitFor(() => expect(screen.getAllByText("Listo")).toHaveLength(1), WAIT);
    await user.type(screen.getByLabelText("Mensaje para Salchi Bot"), "algo barato");
    await user.click(screen.getByRole("button", { name: "Enviar mensaje" }));

    await waitFor(() => expect(mockedAsk).toHaveBeenCalledTimes(2), WAIT);
    expect(mockedAsk).toHaveBeenLastCalledWith("algo barato", ["tengo un gato"]);
  });

  it("lets the customer tap a suggestion instead of typing", async () => {
    const user = userEvent.setup();
    mockedAsk.mockResolvedValue({ reply: "Ofertas", products: [], suggestions: [] });
    setup();
    await user.click(screen.getByRole("button", { name: "Abrir chat con Salchi Bot" }));

    await user.click(screen.getByRole("button", { name: "¿Qué ofertas hay?" }));

    await waitFor(() => expect(mockedAsk).toHaveBeenCalledWith("¿Qué ofertas hay?", []), WAIT);
  });

  it("opens the product page or adds it to the cart from a recommendation", async () => {
    const user = userEvent.setup();
    mockedAsk.mockResolvedValue({ reply: "Mirá", products: [CORREA], suggestions: [] });
    const { onOpenProduct, onAddToCart } = setup();
    await user.click(screen.getByRole("button", { name: "Abrir chat con Salchi Bot" }));
    await user.type(screen.getByLabelText("Mensaje para Salchi Bot"), "correa");
    await user.click(screen.getByRole("button", { name: "Enviar mensaje" }));
    await screen.findByText("Correa reflectante 1.5m", {}, WAIT);

    await user.click(screen.getByRole("button", { name: "Agregar" }));
    expect(onAddToCart).toHaveBeenCalledWith(CORREA);

    await user.click(screen.getByRole("button", { name: "Ver" }));
    expect(onOpenProduct).toHaveBeenCalledWith("p33");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("answers with an apology if the server fails", async () => {
    const user = userEvent.setup();
    mockedAsk.mockRejectedValue(new Error("boom"));
    setup();
    await user.click(screen.getByRole("button", { name: "Abrir chat con Salchi Bot" }));
    await user.type(screen.getByLabelText("Mensaje para Salchi Bot"), "hola");
    await user.click(screen.getByRole("button", { name: "Enviar mensaje" }));

    expect(await screen.findByText(/se me cortó la comunicación/, {}, WAIT)).toBeInTheDocument();
  });

  it("does not send empty messages and closes with the X button or Escape", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(screen.getByRole("button", { name: "Abrir chat con Salchi Bot" }));

    expect(screen.getByRole("button", { name: "Enviar mensaje" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Cerrar chat" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Abrir chat con Salchi Bot" }));
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mockedAsk).not.toHaveBeenCalled();
  });
});
