import { useState } from "react";
import { recoverPassword, resetPassword } from "../api";
import { Icon } from "../components/Icon";

/** Panel decorativo con una mascota: acompaña a todos los formularios de acceso. */
function AuthAside({ title, text }: { title: string; text: string }) {
  return (
    <aside className="auth-aside" aria-hidden="true">
      <img
        src="https://images.unsplash.com/photo-1537204696486-967f1b7198c8?auto=format&fit=crop&w=900&q=80"
        alt=""
        onError={(e) => {
          e.currentTarget.style.visibility = "hidden";
        }}
      />
      <div className="auth-aside-copy">
        <span className="auth-aside-icon">
          <Icon name="paw" size={26} />
        </span>
        <p className="auth-aside-title">{title}</p>
        <p>{text}</p>
      </div>
    </aside>
  );
}

type Props = {
  mode: "login" | "register";
  error: string | null;
  loading: boolean;
  onSubmit: (payload: { email: string; password: string; name?: string }) => void;
  onToggleMode: () => void;
};

export function LoginScreen({ mode, error, loading, onSubmit, onToggleMode }: Props) {
  const [email, setEmail] = useState("cliente@ejemplo.com");
  const [password, setPassword] = useState("password");
  const [name, setName] = useState("");

  const [recoverMode, setRecoverMode] = useState<"request" | "reset" | null>(null);
  const [recoverEmail, setRecoverEmail] = useState("");
  const [recoverToken, setRecoverToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [recoverMessage, setRecoverMessage] = useState<string | null>(null);
  const [recoverError, setRecoverError] = useState<string | null>(null);
  const [recoverLoading, setRecoverLoading] = useState(false);
  const [recoverDone, setRecoverDone] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({ email, password, name: mode === "register" ? name : undefined });
  };

  const closeRecover = () => {
    setRecoverMode(null);
    setRecoverError(null);
    setRecoverMessage(null);
    setRecoverToken("");
    setNewPassword("");
    setRecoverDone(false);
  };

  const handleRecoverRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    setRecoverError(null);
    setRecoverLoading(true);
    try {
      const result = await recoverPassword(recoverEmail);
      // El código nunca viaja en la respuesta: llega por email. En desarrollo no hay servidor de correo y queda en outbox/.
      const devHint = import.meta.env.DEV ? " (Entorno de desarrollo: el mensaje simulado queda en la carpeta outbox/ del backend.)" : "";
      setRecoverMessage(`${result.message} El código vence en ${result.expiresInMinutes} minutos.${devHint}`);
      setRecoverMode("reset");
    } catch (err) {
      setRecoverError(err instanceof Error ? err.message : "No se pudo generar el código");
    } finally {
      setRecoverLoading(false);
    }
  };

  const handleResetSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setRecoverError(null);
    setRecoverLoading(true);
    try {
      await resetPassword(recoverToken, newPassword);
      setRecoverMessage("Contraseña actualizada. Ya podés iniciar sesión con la nueva.");
      setRecoverDone(true);
    } catch (err) {
      setRecoverError(err instanceof Error ? err.message : "No se pudo actualizar la contraseña");
    } finally {
      setRecoverLoading(false);
    }
  };

  if (recoverMode) {
    return (
      <div className="page-shell auth-shell">
        <AuthAside title="No pasa nada, lo resolvemos" text="Te ayudamos a recuperar tu cuenta en un momento." />
        <div className="auth-card">
          <div className="auth-header">
            <span className="eyebrow">Petshop</span>
            <h2>Recuperar contraseña</h2>
          </div>

          {recoverDone ? (
            <>
              {recoverMessage ? <div className="info-box">{recoverMessage}</div> : null}
              <button className="primary-btn block" type="button" onClick={closeRecover}>
                Volver a iniciar sesión
              </button>
            </>
          ) : recoverMode === "request" ? (
            <form onSubmit={handleRecoverRequest} className="auth-form">
              <label>
                <span>Email</span>
                <input
                  type="email"
                  value={recoverEmail}
                  onChange={(e) => setRecoverEmail(e.target.value)}
                  placeholder="mail@ejemplo.com"
                />
              </label>
              {recoverError ? <div className="error-box" role="alert">{recoverError}</div> : null}
              <button className="primary-btn block" type="submit" disabled={recoverLoading}>
                {recoverLoading ? "Enviando..." : "Enviar código"}
              </button>
            </form>
          ) : (
            <form onSubmit={handleResetSubmit} className="auth-form">
              {recoverMessage ? <div className="info-box">{recoverMessage}</div> : null}
              <label>
                <span>Código de recuperación</span>
                <input value={recoverToken} onChange={(e) => setRecoverToken(e.target.value)} placeholder="Pegá el código" />
              </label>
              <label>
                <span>Nueva contraseña</span>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="••••••••"
                />
              </label>
              {recoverError ? <div className="error-box" role="alert">{recoverError}</div> : null}
              <button className="primary-btn block" type="submit" disabled={recoverLoading}>
                {recoverLoading ? "Guardando..." : "Cambiar contraseña"}
              </button>
            </form>
          )}

          {!recoverDone ? (
            <button className="text-link center-link" onClick={closeRecover}>
              Volver a iniciar sesión
            </button>
          ) : null}
        </div>
      </div>
    );
  }

  return (
    <div className="page-shell auth-shell">
      <AuthAside
        title={mode === "login" ? "¡Qué alegría verte de nuevo!" : "Sumate a la familia Petshop"}
        text={
          mode === "login"
            ? "Entrá para ver tus pedidos, tus favoritos y tu carrito."
            : "Creá tu cuenta y guardá tus favoritos, pedidos y descuentos."
        }
      />
      <div className="auth-card">
        <div className="auth-header">
          <span className="eyebrow">
            <Icon name="paw" size={14} /> Petshop
          </span>
          <h2>{mode === "login" ? "Ingresar" : "Crear cuenta"}</h2>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          {mode === "register" ? (
            <label>
              <span>Nombre</span>
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Tu nombre" />
            </label>
          ) : null}

          <label>
            <span>Email</span>
            <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="mail@ejemplo.com" />
          </label>

          <label>
            <span>Contraseña</span>
            <input
              type="password"
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
          </label>

          {error ? <div className="error-box" role="alert">{error}</div> : null}

          <button className="primary-btn block" type="submit" disabled={loading}>
            {loading ? "Procesando..." : mode === "login" ? "Iniciar sesión" : "Registrarme"}
          </button>
        </form>

        {mode === "login" ? (
          <button
            className="text-link center-link"
            onClick={() => {
              setRecoverEmail(email);
              setRecoverMode("request");
            }}
          >
            Olvidé mi contraseña
          </button>
        ) : null}

        <button className="text-link center-link" onClick={onToggleMode}>
          {mode === "login" ? "Crear una cuenta" : "Ya tengo cuenta"}
        </button>
      </div>
    </div>
  );
}
