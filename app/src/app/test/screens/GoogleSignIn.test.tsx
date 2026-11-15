import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { GoogleSignInButton } from "../../components/GoogleSignInButton";
import { fetchAuthConfig } from "../../api";

vi.mock("../../api", () => ({ fetchAuthConfig: vi.fn() }));
const mockedConfig = vi.mocked(fetchAuthConfig);

describe("GoogleSignInButton", () => {
  const initialize = vi.fn();
  const renderButton = vi.fn();

  beforeEach(() => {
    initialize.mockReset();
    renderButton.mockReset();
    // Con el objeto de Google ya presente no hace falta cargar el script real
    window.google = { accounts: { id: { initialize, renderButton } } };
  });

  afterEach(() => {
    delete window.google;
  });

  it("stays hidden when the server has no Google client id", async () => {
    mockedConfig.mockResolvedValue({ googleClientId: null });
    const { container } = render(<GoogleSignInButton onCredential={vi.fn()} />);

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(renderButton).not.toHaveBeenCalled();
    expect(container.querySelector(".google-signin")).toHaveClass("hidden");
  });

  it("shows Google's button with the client id from the server", async () => {
    mockedConfig.mockResolvedValue({ googleClientId: "123.apps.googleusercontent.com" });
    const { container } = render(<GoogleSignInButton onCredential={vi.fn()} />);

    await waitFor(() => expect(renderButton).toHaveBeenCalled());
    expect(initialize).toHaveBeenCalledWith(expect.objectContaining({ client_id: "123.apps.googleusercontent.com", ux_mode: "popup" }));
    expect(renderButton.mock.calls[0][1]).toMatchObject({ locale: "es", text: "continue_with" });
    expect(container.querySelector(".google-signin")).not.toHaveClass("hidden");
  });

  it("hands the token Google gives to the app, and ignores an answer without a token", async () => {
    mockedConfig.mockResolvedValue({ googleClientId: "123.apps.googleusercontent.com" });
    const onCredential = vi.fn();
    render(<GoogleSignInButton onCredential={onCredential} />);
    await waitFor(() => expect(initialize).toHaveBeenCalled());

    const googleCallback = initialize.mock.calls[0][0].callback as (response: { credential?: string }) => void;
    googleCallback({});
    expect(onCredential).not.toHaveBeenCalled();

    googleCallback({ credential: "token-de-google" });
    expect(onCredential).toHaveBeenCalledWith("token-de-google");
  });

  it("does not break the login when the configuration cannot be read", async () => {
    mockedConfig.mockRejectedValue(new Error("sin conexión"));
    const { container } = render(<GoogleSignInButton onCredential={vi.fn()} />);

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(renderButton).not.toHaveBeenCalled();
    expect(container.querySelector(".google-signin")).toHaveClass("hidden");
  });
});
