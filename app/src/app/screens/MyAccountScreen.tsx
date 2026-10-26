import { useState } from "react";
import type { User } from "../types";
import { changePassword, updateMyProfile } from "../api";
import { Icon } from "../components/Icon";

type Props = {
  authToken: string;
  currentUser: User;
  onProfileUpdated: (user: User) => void;
};

export function MyAccountScreen({ authToken, currentUser, onProfileUpdated }: Props) {
  const [name, setName] = useState(currentUser.name);
  const [nameError, setNameError] = useState<string | null>(null);
  const [nameSaving, setNameSaving] = useState(false);
  const [nameSaved, setNameSaved] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordSaved, setPasswordSaved] = useState(false);

  const handleNameSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setNameError(null);
    setNameSaved(false);

    if (!name.trim()) {
      setNameError("El nombre no puede estar vacío.");
      return;
    }

    setNameSaving(true);
    try {
      const updated = await updateMyProfile(authToken, name.trim());
      onProfileUpdated(updated);
      setNameSaved(true);
    } catch (error) {
      setNameError(error instanceof Error ? error.message : "No se pudo guardar el nombre");
    } finally {
      setNameSaving(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);
    setPasswordSaved(false);

    if (!currentPassword || !newPassword) {
      setPasswordError("Completa ambos campos.");
      return;
    }

    setPasswordSaving(true);
    try {
      await changePassword(authToken, currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setPasswordSaved(true);
    } catch (error) {
      setPasswordError(error instanceof Error ? error.message : "No se pudo cambiar la contraseña");
    } finally {
      setPasswordSaving(false);
    }
  };

  return (
    <div className="page-shell account-shell">
      <div className="section-header">
        <h2>Mi cuenta</h2>
      </div>

      <div className="account-hero">
        <span className="account-avatar" aria-hidden="true">
          {(currentUser.name.trim()[0] ?? "?").toUpperCase()}
        </span>
        <div>
          <strong>{currentUser.name}</strong>
          <small>{currentUser.email}</small>
        </div>
        <Icon name="paw" size={28} className="account-hero-paw" />
      </div>

      <div className="account-cards">
        <form className="account-card" onSubmit={handleNameSubmit}>
          <h3>Datos personales</h3>
          <label>
            <span>Email</span>
            <input value={currentUser.email} disabled />
          </label>
          <label>
            <span>Nombre</span>
            <input value={name} autoComplete="name" onChange={(e) => setName(e.target.value)} />
          </label>
          {nameError ? <div className="error-box" role="alert">{nameError}</div> : null}
          {nameSaved ? <div className="info-box">Datos actualizados.</div> : null}
          <button className="primary-btn" type="submit" disabled={nameSaving}>
            {nameSaving ? "Guardando..." : "Guardar cambios"}
          </button>
        </form>

        <form className="account-card" onSubmit={handlePasswordSubmit}>
          <h3>Cambiar contraseña</h3>
          <label>
            <span>Contraseña actual</span>
            <input type="password" autoComplete="current-password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
          </label>
          <label>
            <span>Contraseña nueva</span>
            <input type="password" autoComplete="new-password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
          </label>
          {passwordError ? <div className="error-box" role="alert">{passwordError}</div> : null}
          {passwordSaved ? <div className="info-box">Contraseña actualizada.</div> : null}
          <button className="primary-btn" type="submit" disabled={passwordSaving}>
            {passwordSaving ? "Guardando..." : "Cambiar contraseña"}
          </button>
        </form>
      </div>
    </div>
  );
}
