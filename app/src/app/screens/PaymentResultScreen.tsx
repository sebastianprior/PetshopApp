import { useCallback, useEffect, useRef, useState } from "react";
import type { CheckoutResult, View } from "../types";
import { syncVentaPayment } from "../api";
import { Icon } from "../components/Icon";
import { OrderConfirmationScreen } from "./OrderConfirmationScreen";

type Props = {
  /** Número de pedido que viene en la dirección a la que MercadoPago devuelve al cliente (?orden=12). */
  orderId: number | null;
  /** Lo que MercadoPago agrega al volver (status=rejected, etc.): solo orienta el mensaje; la verdad se le pregunta al servidor. */
  returnedStatus?: string | null;
  isLoggedIn: boolean;
  onNavigate: (view: View) => void;
};

const POLL_MS = 5000;
const MAX_CHECKS = 24; // ~2 minutos esperando que MercadoPago confirme

/** Pantalla a la que vuelve el cliente después de pagar en MercadoPago: confirma el pago con el servidor y muestra el resultado. */
export function PaymentResultScreen({ orderId, returnedStatus = null, isLoggedIn, onNavigate }: Props) {
  const [result, setResult] = useState<CheckoutResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(true);
  const checks = useRef(0);

  const check = useCallback(async () => {
    if (orderId == null) return;
    setChecking(true);
    try {
      const updated = await syncVentaPayment(orderId);
      setResult(updated);
      setError(null);
      checks.current += 1;
    } catch (err) {
      setError(err instanceof Error ? err.message : "No pudimos verificar el pago");
    } finally {
      setChecking(false);
    }
  }, [orderId]);

  useEffect(() => {
    checks.current = 0;
    void check();
  }, [check]);

  // Mientras el pago siga pendiente, se vuelve a preguntar cada pocos segundos
  const pending = result?.estado === "PAGO_PENDIENTE";
  useEffect(() => {
    if (!pending) return;
    const timer = setInterval(() => {
      if (checks.current >= MAX_CHECKS) {
        clearInterval(timer);
        return;
      }
      void check();
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [pending, check]);

  if (orderId == null) {
    return (
      <div className="page-shell confirmation-shell">
        <div className="confirmation-card">
          <h2>No encontramos ese pedido</h2>
          <p>Si ya pagaste, revisá "Mis pedidos" para ver el estado de tu compra.</p>
          <div className="confirmation-actions">
            <button className="primary-btn" onClick={() => onNavigate(isLoggedIn ? "orders" : "home")}>
              {isLoggedIn ? "Ver mis pedidos" : "Ir al inicio"}
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!result) {
    return (
      <div className="page-shell confirmation-shell">
        <div className="confirmation-card" aria-live="polite">
          {error ? (
            <>
              <span className="confirmation-check pending">
                <Icon name="package" size={38} />
              </span>
              <h2>No pudimos verificar tu pago</h2>
              <p>{error}. Tu pedido #{orderId} sigue registrado: probá de nuevo en un momento.</p>
              <div className="confirmation-actions">
                <button className="primary-btn" onClick={() => void check()} disabled={checking}>
                  {checking ? "Verificando..." : "Verificar de nuevo"}
                </button>
              </div>
            </>
          ) : (
            <>
              <span className="confirmation-check pending">
                <Icon name="card" size={38} />
              </span>
              <h2>Confirmando tu pago...</h2>
              <p>Estamos consultando a MercadoPago. Solo tarda unos segundos.</p>
            </>
          )}
        </div>
      </div>
    );
  }

  if (result.estado === "COMPLETADA" || result.estado === "ENVIADA") {
    return <OrderConfirmationScreen result={result} isLoggedIn={isLoggedIn} onNavigate={onNavigate} />;
  }

  if (result.estado === "CANCELADA") {
    return (
      <div className="page-shell confirmation-shell">
        <div className="confirmation-card">
          <span className="confirmation-check pending">
            <Icon name="close" size={38} />
          </span>
          <h2>El pago no se completó</h2>
          <p>
            El pedido #{result.orderId} se canceló y no se te cobró nada. Podés volver a armar tu carrito y probar de nuevo.
          </p>
          <div className="confirmation-actions">
            <button className="primary-btn" onClick={() => onNavigate("products")}>
              Volver a la tienda
            </button>
          </div>
        </div>
      </div>
    );
  }

  const rejected = returnedStatus === "rejected" || returnedStatus === "failure";
  return (
    <div className="page-shell confirmation-shell">
      <div className="confirmation-card" aria-live="polite">
        <span className="confirmation-check pending">
          <Icon name="package" size={38} />
        </span>
        <h2>{rejected ? "El pago no se completó" : "Todavía esperamos la confirmación del pago"}</h2>
        <p>
          {rejected
            ? `MercadoPago no aprobó el pago. Tu pedido #${result.orderId} sigue reservado unos minutos: podés intentar de nuevo con otro medio de pago.`
            : `Tu pedido #${result.orderId} está reservado. Apenas MercadoPago confirme el pago, lo vas a ver acá y en "Mis pedidos". Si ya pagaste, puede demorar unos minutos.`}
        </p>
        <div className="confirmation-actions">
          {result.paymentUrl ? (
            <a className="primary-btn" href={result.paymentUrl}>
              {rejected ? "Intentar de nuevo" : "Ir a pagar"}
            </a>
          ) : null}
          <button className="secondary-btn" onClick={() => void check()} disabled={checking}>
            {checking ? "Verificando..." : "Verificar de nuevo"}
          </button>
        </div>
      </div>
    </div>
  );
}
