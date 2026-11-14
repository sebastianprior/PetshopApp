import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LoginScreen } from "../../screens/LoginScreen";
import { recoverPassword, resetPassword } from "../../api";

vi.mock("../../api", () => ({
  recoverPassword: vi.fn(),
  resetPassword: vi.fn(),
}));

const mockedRecoverPassword = vi.mocked(recoverPassword);
const mockedResetPassword = vi.mocked(resetPassword);

describe("LoginScreen", () => {
  beforeEach(() => {
    mockedRecoverPassword.mockReset();
    mockedResetPassword.mockReset();
  });

  it("renders the login form without a name field", () => {
    render(<LoginScreen mode="login" error={null} loading={false} onSubmit={vi.fn()} onToggleMode={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Ingresar" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Nombre")).not.toBeInTheDocument();
  });

  it("renders the register form with a name field", () => {
    render(<LoginScreen mode="register" error={null} loading={false} onSubmit={vi.fn()} onToggleMode={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Crear cuenta" })).toBeInTheDocument();
    expect(screen.getByLabelText("Nombre")).toBeInTheDocument();
  });

  it("submits the entered credentials", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(<LoginScreen mode="login" error={null} loading={false} onSubmit={onSubmit} onToggleMode={vi.fn()} />);

    await user.clear(screen.getByLabelText("Email"));
    await user.type(screen.getByLabelText("Email"), "test@test.com");
    await user.clear(screen.getByLabelText("Contraseña"));
    await user.type(screen.getByLabelText("Contraseña"), "secret123");
    await user.click(screen.getByText("Iniciar sesión"));

    expect(onSubmit).toHaveBeenCalledWith({ email: "test@test.com", password: "secret123", name: undefined });
  });

  it("includes the name when submitting in register mode", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    render(<LoginScreen mode="register" error={null} loading={false} onSubmit={onSubmit} onToggleMode={vi.fn()} />);

    await user.type(screen.getByLabelText("Nombre"), "Juan");
    await user.click(screen.getByText("Registrarme"));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ name: "Juan" }));
  });

  it("shows the error message and disables the button while loading", () => {
    render(<LoginScreen mode="login" error="Credenciales inválidas" loading onSubmit={vi.fn()} onToggleMode={vi.fn()} />);

    expect(screen.getByText("Credenciales inválidas")).toBeInTheDocument();
    expect(screen.getByText("Procesando...")).toBeDisabled();
  });

  it("calls onToggleMode when switching between login and register", async () => {
    const user = userEvent.setup();
    const onToggleMode = vi.fn();

    render(<LoginScreen mode="login" error={null} loading={false} onSubmit={vi.fn()} onToggleMode={onToggleMode} />);

    await user.click(screen.getByText("Crear una cuenta"));

    expect(onToggleMode).toHaveBeenCalled();
  });

  it("walks through the forgot-password flow end to end", async () => {
    const user = userEvent.setup();
    mockedRecoverPassword.mockResolvedValue({ ok: true, message: "Si el email está registrado, te enviamos un código.", expiresInMinutes: 15 });
    mockedResetPassword.mockResolvedValue({ ok: true });

    render(<LoginScreen mode="login" error={null} loading={false} onSubmit={vi.fn()} onToggleMode={vi.fn()} />);

    await user.click(screen.getByText("Olvidé mi contraseña"));
    expect(screen.getByRole("heading", { name: "Recuperar contraseña" })).toBeInTheDocument();

    await user.click(screen.getByText("Enviar código"));

    await waitFor(() => expect(screen.getByText(/Si el email está registrado/)).toBeInTheDocument());
    expect(mockedRecoverPassword).toHaveBeenCalled();
    // el código nunca se muestra en pantalla: hay que pegarlo desde el email
    expect(screen.getByLabelText("Código de recuperación")).toHaveValue("");

    await user.type(screen.getByLabelText("Código de recuperación"), "ABC123");
    await user.type(screen.getByLabelText("Nueva contraseña"), "newpass123");
    await user.click(screen.getByText("Cambiar contraseña"));

    await waitFor(() => expect(screen.getByText(/Contraseña actualizada/)).toBeInTheDocument());
    expect(mockedResetPassword).toHaveBeenCalledWith("ABC123", "newpass123");

    await user.click(screen.getByText("Volver a iniciar sesión"));
    expect(screen.getByRole("heading", { name: "Ingresar" })).toBeInTheDocument();
  });

  it("shows an error if requesting the recovery code fails", async () => {
    const user = userEvent.setup();
    mockedRecoverPassword.mockRejectedValue(new Error("Email no registrado"));

    render(<LoginScreen mode="login" error={null} loading={false} onSubmit={vi.fn()} onToggleMode={vi.fn()} />);

    await user.click(screen.getByText("Olvidé mi contraseña"));
    await user.click(screen.getByText("Enviar código"));

    await waitFor(() => expect(screen.getByText("Email no registrado")).toBeInTheDocument());
  });
});
