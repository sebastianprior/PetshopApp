import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DailyRoulette, SPIN_MS } from "../../screens/DailyRoulette";
import { spinRoulette } from "../../api";
import type { RouletteState } from "../../types";

vi.mock("../../api", () => ({ spinRoulette: vi.fn() }));
const mockedSpin = vi.mocked(spinRoulette);

const CATEGORIES = [
  { id: "gatos", name: "Gatos", color: "#BFEAF5" },
  { id: "higiene", name: "Higiene", color: "#D1FAE5" },
  { id: "juguetes", name: "Juguetes", color: "#FDE2E4" },
  { id: "perros", name: "Perros", color: "#FFD6A5" },
];

const FRESH: RouletteState = { percent: 10, categories: CATEGORIES, spun: false, categoryId: null, categoryName: null, validUntil: null };
const WON: RouletteState = { ...FRESH, spun: true, categoryId: "juguetes", categoryName: "Juguetes", validUntil: "2026-10-20T03:00:00Z", alreadySpun: false };

function setup(state: RouletteState) {
  const onSpun = vi.fn();
  const onClose = vi.fn();
  const onGoToCategory = vi.fn();
  render(<DailyRoulette state={state} token="tok" onSpun={onSpun} onClose={onClose} onGoToCategory={onGoToCategory} />);
  return { onSpun, onClose, onGoToCategory };
}

describe("DailyRoulette", () => {
  beforeEach(() => {
    mockedSpin.mockReset();
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows one segment per category and invites to spin", () => {
    const { container } = render(
      <DailyRoulette state={FRESH} token={null} onSpun={vi.fn()} onClose={vi.fn()} onGoToCategory={vi.fn()} />,
    );

    expect(screen.getByRole("dialog", { name: "Descuento del día" })).toBeInTheDocument();
    expect(screen.getByText("Girá y ganá 10% OFF")).toBeInTheDocument();
    expect(container.querySelectorAll(".roulette-wheel path")).toHaveLength(4);
    ["Gatos", "Higiene", "Juguetes", "Perros"].forEach((name) => expect(container.querySelector(".roulette-wheel")?.textContent).toContain(name));
    expect(screen.getByRole("button", { name: "¡Girar!" })).toBeEnabled();
  });

  it("asks the server for the result, spins to that segment and then announces the prize", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    mockedSpin.mockResolvedValue(WON);
    const { onSpun } = setup(FRESH);

    await user.click(screen.getByRole("button", { name: "¡Girar!" }));

    expect(mockedSpin).toHaveBeenCalledWith("tok");
    expect(screen.getByRole("button", { name: "Girando…" })).toBeDisabled();
    // el gajo "Juguetes" (índice 2 de 4) tiene que quedar bajo la flecha: 6 vueltas + (360 - 225)
    const wheel = document.querySelector(".roulette-wheel") as SVGElement;
    expect(wheel.style.transform).toBe(`rotate(${360 * 6 + 135}deg)`);
    expect(onSpun).not.toHaveBeenCalled();

    await act(async () => {
      vi.advanceTimersByTime(SPIN_MS);
    });

    expect(screen.getByText("¡10% OFF en Juguetes!")).toBeInTheDocument();
    expect(onSpun).toHaveBeenCalledWith(WON);
    expect(screen.getByRole("button", { name: "Ver Juguetes" })).toBeInTheDocument();
  });

  it("when the person already spun today it shows the prize right away and points the wheel at it", () => {
    setup({ ...WON, alreadySpun: true });

    expect(screen.getByText("¡10% OFF en Juguetes!")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "¡Girar!" })).not.toBeInTheDocument();
    expect(screen.getByText(/Ya giraste hoy/)).toBeInTheDocument();
    expect((document.querySelector(".roulette-wheel") as SVGElement).style.transform).toBe("rotate(135deg)");
  });

  it("lets the customer jump to the winning category or close the window", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { onGoToCategory, onClose } = setup(WON);

    await user.click(screen.getByRole("button", { name: "Ver Juguetes" }));
    expect(onGoToCategory).toHaveBeenCalledWith("juguetes");

    await user.click(screen.getByRole("button", { name: "Seguir mirando" }));
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("shows the error and lets the customer try again if the server fails", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    mockedSpin.mockRejectedValueOnce(new Error("No se pudo girar la ruleta"));
    setup(FRESH);

    await user.click(screen.getByRole("button", { name: "¡Girar!" }));

    expect(await screen.findByText("No se pudo girar la ruleta")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "¡Girar!" })).toBeEnabled();
  });
});
