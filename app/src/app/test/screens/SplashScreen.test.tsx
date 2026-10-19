import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SPLASH_WALK_MS, SplashScreen } from "../../screens/SplashScreen";

describe("SplashScreen", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("announces itself as a loading status and shows the walking dog", () => {
    const { container } = render(<SplashScreen onDone={vi.fn()} />);

    expect(screen.getByRole("status", { name: "Cargando Petshop" })).toBeInTheDocument();
    expect(container.querySelector(".splash-dog svg")).not.toBeNull();
    expect(container.querySelectorAll(".dog-leg")).toHaveLength(4);
  });

  it("fades out after the walk and then calls onDone", () => {
    const onDone = vi.fn();
    render(<SplashScreen onDone={onDone} />);
    const splash = screen.getByRole("status");

    expect(splash).not.toHaveClass("leaving");
    act(() => {
      vi.advanceTimersByTime(SPLASH_WALK_MS);
    });
    expect(splash).toHaveClass("leaving");
    expect(onDone).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it("can be skipped with a tap", () => {
    const onDone = vi.fn();
    render(<SplashScreen onDone={onDone} />);

    fireEvent.click(screen.getByRole("status"));
    act(() => {
      vi.advanceTimersByTime(500);
    });

    expect(screen.getByRole("status")).toHaveClass("leaving");
    expect(onDone).toHaveBeenCalled();
  });

  it("does not call onDone after being unmounted", () => {
    const onDone = vi.fn();
    const { unmount } = render(<SplashScreen onDone={onDone} />);

    unmount();
    act(() => {
      vi.advanceTimersByTime(SPLASH_WALK_MS + 1000);
    });

    expect(onDone).not.toHaveBeenCalled();
  });
});
