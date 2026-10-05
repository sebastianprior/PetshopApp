import type { CheckoutResult, View } from "../types";

type Props = {
  result: CheckoutResult;
  isLoggedIn: boolean;
  onNavigate: (view: View) => void;
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

export function OrderConfirmationScreen({ result, isLoggedIn, onNavigate }: Props) {
  const pagoPendiente = result.estado === "PAGO_PENDIENTE";

  return (
    <div className="page-shell confirmation-shell">
      <div className="confirmation-card">
        {pagoPendiente ? (
          <>
            <span className="confirmation-check">…</span>
            <h2>Estamos procesando tu pago</h2>
            <p>
              La pasarela de pago tardó en responder. Tu pedido #{result.orderId} quedó reservado y lo confirmamos
              automáticamente en cuanto se acredite el pago; si no se acredita, se cancela y se libera el stock.
              Podés seguir el estado en "Mis pedidos".
            </p>
          </>
        ) : (
          <>
            <span className="confirmation-check">✓</span>
            <h2>¡Compra confirmada!</h2>
            <p>
              {result.orderId != null
                ? `Tu pedido #${result.orderId} se registró con éxito.`
                : "Tu compra se realizó con éxito."}
            </p>
          </>
        )}

        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Producto</th>
                <th>Variante</th>
                <th>Cantidad</th>
                <th>Precio</th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((item, idx) => (
                <tr key={`${item.productId}-${idx}`}>
                  <td>{item.name}</td>
                  <td>{item.variant || "—"}</td>
                  <td>{item.quantity}</td>
                  <td>{formatMoney(item.price)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="confirmation-totals">
          <div className="summary-row">
            <span>Subtotal</span>
            <strong>{formatMoney(result.subtotal)}</strong>
          </div>
          {result.discountAmount > 0 ? (
            <div className="summary-row discount-row">
              <span>Descuento {result.couponCode ? `(${result.couponCode})` : ""}</span>
              <strong>-{formatMoney(result.discountAmount)}</strong>
            </div>
          ) : null}
          <div className="summary-row">
            <span>Envío</span>
            <strong>{formatMoney(result.shippingCost)}</strong>
          </div>
          <div className="summary-row total-row">
            <span>Total</span>
            <strong>{formatMoney(result.total)}</strong>
          </div>
        </div>

        <div className="confirmation-actions">
          {result.orderId != null && isLoggedIn ? (
            <button className="primary-btn" onClick={() => onNavigate("orders")}>Ver mis pedidos</button>
          ) : null}
          <button className="secondary-btn" onClick={() => onNavigate("products")}>Seguir comprando</button>
        </div>
      </div>
    </div>
  );
}
