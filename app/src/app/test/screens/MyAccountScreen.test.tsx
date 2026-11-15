import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MyAccountScreen } from "../../screens/MyAccountScreen";
import type { User } from "../../types";

vi.mock("../../api", () => ({ changePassword: vi.fn(), updateMyProfile: vi.fn() }));

const base: User = { id: "u1", email: "persona@gmail.com", name: "Persona", role: "CUSTOMER", active: true };

describe("MyAccountScreen", () => {
  it("lets a customer with email and password change the password", () => {
    render(<MyAccountScreen authToken="tok" currentUser={{ ...base, authProvider: "LOCAL" }} onProfileUpdated={vi.fn()} />);

    expect(screen.getByLabelText("Contraseña actual")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cambiar contraseña" })).toBeInTheDocument();
  });

  it("treats an account without the provider as a normal one (older sessions)", () => {
    render(<MyAccountScreen authToken="tok" currentUser={base} onProfileUpdated={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Cambiar contraseña" })).toBeInTheDocument();
  });

  it("does not offer to change the password of a Google account, and explains why", () => {
    render(<MyAccountScreen authToken="tok" currentUser={{ ...base, authProvider: "GOOGLE" }} onProfileUpdated={vi.fn()} />);

    expect(screen.queryByLabelText("Contraseña actual")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cambiar contraseña" })).not.toBeInTheDocument();
    expect(screen.getByText(/Ingresás con tu cuenta de Google/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /seguridad de mi cuenta de Google/ })).toHaveAttribute("href", "https://myaccount.google.com/security");
  });

  it("still lets a Google user edit their name", () => {
    render(<MyAccountScreen authToken="tok" currentUser={{ ...base, authProvider: "GOOGLE" }} onProfileUpdated={vi.fn()} />);

    expect(screen.getByLabelText("Nombre")).toHaveValue("Persona");
    expect(screen.getByRole("button", { name: "Guardar cambios" })).toBeInTheDocument();
  });
});
