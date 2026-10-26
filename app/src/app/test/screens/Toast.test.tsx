import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ToastRegion } from "../../components/Toast";

describe("ToastRegion", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders nothing visible without a message but keeps the live region", () => {
    render(<ToastRegion toast={null} onDismiss={vi.fn()} />);

    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("shows the message and runs the action, then closes", async () => {
    const user = userEvent.setup();
    const onAction = vi.fn();
    const onDismiss = vi.fn();
    render(<ToastRegion toast={{ id: 1, text: "Croquetas se agregó al carrito", actionLabel: "Ver carrito", onAction }} onDismiss={onDismiss} />);

    expect(screen.getByText("Croquetas se agregó al carrito")).toBeInTheDocument();
    await user.click(screen.getByText("Ver carrito"));

    expect(onAction).toHaveBeenCalledTimes(1);
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("dismisses itself after a few seconds", () => {
    vi.useFakeTimers();
    const onDismiss = vi.fn();
    render(<ToastRegion toast={{ id: 1, text: "Listo" }} onDismiss={onDismiss} />);

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("keeps error messages on screen longer", () => {
    vi.useFakeTimers();
    const onDismiss = vi.fn();
    render(<ToastRegion toast={{ id: 1, text: "No hay stock", tone: "error" }} onDismiss={onDismiss} />);

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(onDismiss).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(2500);
    });
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});
