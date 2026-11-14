import { useEffect, useRef, useState } from "react";
import { fetchAuthConfig } from "../api";

/** Lo mínimo que se usa del script de Google Identity Services. */
type GoogleAccounts = {
  accounts: {
    id: {
      initialize: (config: { client_id: string; callback: (response: { credential?: string }) => void; ux_mode?: "popup" | "redirect" }) => void;
      renderButton: (element: HTMLElement, options: Record<string, string | number>) => void;
    };
  };
};

declare global {
  interface Window {
    google?: GoogleAccounts;
  }
}

const SCRIPT_URL = "https://accounts.google.com/gsi/client";
let scriptPromise: Promise<void> | null = null;

/** Carga el script de Google una sola vez. Si no se puede (sin conexión, bloqueado), se rechaza y el botón no se muestra. */
function loadGoogleScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!scriptPromise) {
    scriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement("script");
      script.src = SCRIPT_URL;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => {
        scriptPromise = null;
        reject(new Error("No se pudo cargar el script de Google"));
      };
      document.head.appendChild(script);
    });
  }
  return scriptPromise;
}

type Props = {
  /** Recibe el token que firma Google; el servidor lo verifica antes de abrir la sesión. */
  onCredential: (credential: string) => void;
  disabled?: boolean;
};

/** Botón "Continuar con Google". No se muestra si el servidor no tiene Google configurado o el script no carga. */
export function GoogleSignInButton({ onCredential, disabled = false }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const callback = useRef(onCredential);
  callback.current = onCredential;
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const { googleClientId } = await fetchAuthConfig();
        if (!googleClientId) return;
        await loadGoogleScript();
        if (!active || !container.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: googleClientId,
          ux_mode: "popup",
          callback: (response) => {
            if (response.credential) callback.current(response.credential);
          },
        });
        window.google.accounts.id.renderButton(container.current, {
          type: "standard",
          theme: "outline",
          size: "large",
          shape: "pill",
          text: "continue_with",
          locale: "es",
          width: Math.min(340, container.current.clientWidth || 340),
        });
        setReady(true);
      } catch {
        // Sin Google la tienda sigue funcionando con email y contraseña
      }
    })().catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className={ready ? "google-signin" : "google-signin hidden"} aria-hidden={!ready} data-disabled={disabled || undefined}>
      <div className="google-divider">
        <span>o continuá con</span>
      </div>
      <div ref={container} className="google-button" style={disabled ? { pointerEvents: "none", opacity: 0.6 } : undefined} />
    </div>
  );
}
