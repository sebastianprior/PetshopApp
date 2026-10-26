import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FaqScreen } from "../../screens/FaqScreen";
import { FaqSettings } from "../../screens/FaqSettings";
import { updateFaq } from "../../api";
import type { Faq } from "../../types";

vi.mock("../../api", () => ({ updateFaq: vi.fn() }));
const mockedUpdate = vi.mocked(updateFaq);

const ITEMS: Faq[] = [
  { id: 1, question: "¿Cuánto tarda en llegar mi pedido?", answer: "Entre 2 y 5 días hábiles." },
  { id: 2, question: "¿Cómo devuelvo un producto?", answer: "Desde Mis pedidos." },
  { id: 3, question: "¿Qué medios de pago aceptan?", answer: "Tarjeta, hasta 6 cuotas." },
];

describe("FaqScreen", () => {
  it("lists the questions collapsed and opens one answer at a time", async () => {
    const user = userEvent.setup();
    render(<FaqScreen items={ITEMS} onContact={vi.fn()} />);

    expect(screen.getByRole("heading", { level: 1, name: "Preguntas frecuentes" })).toBeInTheDocument();
    expect(screen.queryByText("Entre 2 y 5 días hábiles.")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Cuánto tarda/ }));
    expect(screen.getByText("Entre 2 y 5 días hábiles.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Cuánto tarda/ })).toHaveAttribute("aria-expanded", "true");

    await user.click(screen.getByRole("button", { name: /devuelvo/ }));
    expect(screen.getByText("Desde Mis pedidos.")).toBeInTheDocument();
    expect(screen.queryByText("Entre 2 y 5 días hábiles.")).not.toBeInTheDocument();
  });

  it("filters by what the customer types, ignoring accents", async () => {
    const user = userEvent.setup();
    render(<FaqScreen items={ITEMS} onContact={vi.fn()} />);

    await user.type(screen.getByPlaceholderText(/Buscá tu duda/), "cuotas");

    expect(screen.getByRole("button", { name: /medios de pago/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /devuelvo/ })).not.toBeInTheDocument();
    // con un único resultado se muestra la respuesta abierta
    expect(screen.getByText("Tarjeta, hasta 6 cuotas.")).toBeInTheDocument();
  });

  it("finds related forms of a word (devolver -> devolución)", async () => {
    const user = userEvent.setup();
    render(<FaqScreen items={[...ITEMS, { id: 4, question: "¿Aceptan cambios?", answer: "Sí, pedí la devolución en Mis pedidos." }]} onContact={vi.fn()} />);

    await user.type(screen.getByPlaceholderText(/Buscá tu duda/), "devolver");

    expect(screen.getByRole("button", { name: /Aceptan cambios/ })).toBeInTheDocument();
  });

  it("says so when nothing matches and offers to clear the search", async () => {
    const user = userEvent.setup();
    render(<FaqScreen items={ITEMS} onContact={vi.fn()} />);

    await user.type(screen.getByPlaceholderText(/Buscá tu duda/), "zzzz");
    expect(screen.getByText("No encontramos preguntas con esa búsqueda.")).toBeInTheDocument();

    await user.click(screen.getByText("Ver todas"));
    expect(screen.getAllByRole("button", { name: /\?/ })).toHaveLength(3);
  });

  it("shows empty and loading states and lets the customer contact the store", async () => {
    const user = userEvent.setup();
    const onContact = vi.fn();
    const { rerender } = render(<FaqScreen items={[]} loading onContact={onContact} />);
    expect(screen.getByText("Cargando preguntas...")).toBeInTheDocument();

    rerender(<FaqScreen items={[]} onContact={onContact} />);
    expect(screen.getByText("Todavía no hay preguntas frecuentes.")).toBeInTheDocument();

    await user.click(screen.getByText("Contactanos"));
    expect(onContact).toHaveBeenCalled();
  });
});

describe("FaqSettings", () => {
  beforeEach(() => {
    mockedUpdate.mockReset();
  });

  it("edits, reorders, adds and deletes questions and saves them in that order", async () => {
    const user = userEvent.setup();
    const onSaved = vi.fn();
    mockedUpdate.mockImplementation(async (_token, items) => items.map((item, i) => ({ id: i + 10, ...item })));
    render(<FaqSettings authToken="tok" items={ITEMS} onSaved={onSaved} />);

    // edita la respuesta de la primera
    const answers = screen.getAllByLabelText("Respuesta");
    await user.clear(answers[0]);
    await user.type(answers[0], "En 24 horas.");
    // sube la segunda y borra la tercera
    await user.click(screen.getByRole("button", { name: "Subir la pregunta 2" }));
    await user.click(screen.getByRole("button", { name: "Borrar la pregunta 3" }));
    // agrega una nueva
    await user.click(screen.getByText("Agregar pregunta"));
    const questions = screen.getAllByLabelText("Pregunta");
    await user.type(questions[2], "¿Tienen local?");
    await user.type(screen.getAllByLabelText("Respuesta")[2], "Sí, en Palermo.");
    await user.click(screen.getByText("Guardar cambios"));

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith("tok", [
        { question: "¿Cómo devuelvo un producto?", answer: "Desde Mis pedidos." },
        { question: "¿Cuánto tarda en llegar mi pedido?", answer: "En 24 horas." },
        { question: "¿Tienen local?", answer: "Sí, en Palermo." },
      ]),
    );
    expect(onSaved).toHaveBeenCalledTimes(1);
    expect(await screen.findByText(/Cambios guardados/)).toBeInTheDocument();
  });

  it("does not send incomplete questions", async () => {
    const user = userEvent.setup();
    render(<FaqSettings authToken="tok" items={ITEMS} onSaved={vi.fn()} />);

    await user.click(screen.getByText("Agregar pregunta"));
    await user.click(screen.getByText("Guardar cambios"));

    expect(screen.getByText(/La pregunta 4 necesita pregunta y respuesta/)).toBeInTheDocument();
    expect(mockedUpdate).not.toHaveBeenCalled();
  });

  it("shows the server error", async () => {
    const user = userEvent.setup();
    mockedUpdate.mockRejectedValue(new Error("Requiere permisos de administrador"));
    render(<FaqSettings authToken="tok" items={ITEMS} onSaved={vi.fn()} />);

    await user.click(screen.getByText("Guardar cambios"));

    expect(await screen.findByText("Requiere permisos de administrador")).toBeInTheDocument();
  });

  it("disables moving the first question up and the last one down", () => {
    render(<FaqSettings authToken="tok" items={ITEMS} onSaved={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Subir la pregunta 1" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Bajar la pregunta 3" })).toBeDisabled();
  });
});
