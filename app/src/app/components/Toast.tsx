import { useEffect } from "react";
import { Icon } from "./Icon";

export type ToastMessage = {
  id: number;
  text: string;
  /** "error" para avisos de problemas; por defecto es de éxito. */
  tone?: "success" | "error";
  /** Acción opcional del aviso (por ejemplo, "Ver carrito"). */
  actionLabel?: string;
  onAction?: () => void;
};

type Props = {
  toast: ToastMessage | null;
  onDismiss: () => void;
};

/** Aviso flotante que confirma una acción (ej.: producto agregado al carrito) sin sacar al usuario de la pantalla. */
export function ToastRegion({ toast, onDismiss }: Props) {
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(onDismiss, toast.tone === "error" ? 7000 : 4500);
    return () => clearTimeout(timer);
  }, [toast, onDismiss]);

  return (
    <div className="toast-region" role="status" aria-live="polite">
      {toast ? (
        <div className={toast.tone === "error" ? "toast toast-error" : "toast"} key={toast.id}>
          <span className="toast-icon">
            <Icon name={toast.tone === "error" ? "close" : "check"} size={18} />
          </span>
          <span className="toast-text">{toast.text}</span>
          {toast.actionLabel && toast.onAction ? (
            <button
              className="toast-action"
              onClick={() => {
                toast.onAction?.();
                onDismiss();
              }}
            >
              {toast.actionLabel}
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
