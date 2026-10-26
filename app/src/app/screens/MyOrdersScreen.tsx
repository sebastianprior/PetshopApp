import { useEffect, useState } from "react";
import type { OrderRecord, ReturnRecord } from "../types";
import { fetchMyOrders, fetchMyReturns, requestReturn } from "../api";
import { statusTone } from "../components/statusTone";

type Props = {
  authToken: string;
};

type Tab = "orders" | "returns";

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

type ReturnTarget = {
  orderId: number;
  productId: string;
  productName: string;
  maxQty: number;
  variantId?: number | null;
  variant?: string | null;
};

export function MyOrdersScreen({ authToken }: Props) {
  const [tab, setTab] = useState<Tab>("orders");

  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);

  const [returns, setReturns] = useState<ReturnRecord[]>([]);
  const [returnsLoading, setReturnsLoading] = useState(false);

  const [returnTarget, setReturnTarget] = useState<ReturnTarget | null>(null);
  const [returnQty, setReturnQty] = useState("1");
  const [returnMotivo, setReturnMotivo] = useState("");
  const [returnError, setReturnError] = useState<string | null>(null);
  const [returnSubmitting, setReturnSubmitting] = useState(false);

  const loadOrders = async () => {
    setOrdersLoading(true);
    try {
      setOrders(await fetchMyOrders(authToken));
    } catch (error) {
      console.error("No se pudieron cargar tus pedidos", error);
    } finally {
      setOrdersLoading(false);
    }
  };

  const loadReturns = async () => {
    setReturnsLoading(true);
    try {
      setReturns(await fetchMyReturns(authToken));
    } catch (error) {
      console.error("No se pudieron cargar tus devoluciones", error);
    } finally {
      setReturnsLoading(false);
    }
  };

  useEffect(() => {
    void loadOrders();
    void loadReturns();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startReturn = (target: ReturnTarget) => {
    setReturnTarget(target);
    setReturnQty("1");
    setReturnMotivo("");
    setReturnError(null);
  };

  const cancelReturn = () => {
    setReturnTarget(null);
    setReturnError(null);
  };

  const submitReturn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!returnTarget) return;
    setReturnError(null);

    const cantidad = Number(returnQty);
    if (!cantidad || cantidad < 1 || cantidad > returnTarget.maxQty) {
      setReturnError(`La cantidad debe ser entre 1 y ${returnTarget.maxQty}.`);
      return;
    }
    if (!returnMotivo.trim()) {
      setReturnError("Contanos el motivo de la devolución.");
      return;
    }

    setReturnSubmitting(true);
    try {
      await requestReturn(
        authToken,
        returnTarget.productId,
        cantidad,
        returnMotivo.trim(),
        returnTarget.variantId,
        returnTarget.variant,
      );
      setReturnTarget(null);
      setTab("returns");
      await loadReturns();
    } catch (error) {
      setReturnError(error instanceof Error ? error.message : "No se pudo solicitar la devolución");
    } finally {
      setReturnSubmitting(false);
    }
  };

  return (
    <div className="page-shell admin-shell">
      <div className="section-header">
        <h2>Mis pedidos</h2>
      </div>

      <div className="admin-tabs">
        <button className={tab === "orders" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("orders")}>
          Pedidos
        </button>
        <button className={tab === "returns" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("returns")}>
          Mis devoluciones {returns.length > 0 ? `(${returns.length})` : ""}
        </button>
      </div>

      {tab === "orders" ? (
        <section className="admin-section">
          {ordersLoading ? (
            <div className="empty-state">Cargando tus pedidos...</div>
          ) : orders.length === 0 ? (
            <div className="empty-state">Todavía no hiciste ninguna compra.</div>
          ) : (
            <div className="my-orders-list">
              {orders.map((order) => (
                <div key={order.id} className="my-order-card">
                  <div className="my-order-header">
                    <div>
                      <strong>Pedido #{order.id}</strong>
                      <span className="my-order-date">{new Date(order.fecha).toLocaleString("es-AR")}</span>
                    </div>
                    <div className="my-order-header-right">
                      <span className={`status-badge tone-${statusTone(order.estado)}`}>{order.estado}</span>
                      <strong>{formatMoney(order.total)}</strong>
                    </div>
                  </div>

                  <div className="admin-table-wrap">
                    <table className="admin-table">
                      <thead>
                        <tr>
                          <th>Producto</th>
                          <th>Variante</th>
                          <th>Cantidad</th>
                          <th>Precio</th>
                          <th>Acción</th>
                        </tr>
                      </thead>
                      <tbody>
                        {order.items.map((item, idx) => (
                          <tr key={`${order.id}-${item.productId}-${idx}`}>
                            <td>{item.productId}</td>
                            <td>{item.variant || "—"}</td>
                            <td>{item.quantity}</td>
                            <td>{item.price === 0 ? "Gratis (regalo)" : formatMoney(item.price)}</td>
                            <td>
                              <button
                                className="secondary-btn"
                                onClick={() =>
                                  startReturn({
                                    orderId: order.id,
                                    productId: item.productId,
                                    productName: item.productId,
                                    maxQty: item.quantity,
                                    variantId: item.variantId,
                                    variant: item.variant,
                                  })
                                }
                              >
                                Solicitar devolución
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  {returnTarget && returnTarget.orderId === order.id ? (
                    <form className="return-form" onSubmit={submitReturn}>
                      <p>
                        Devolver <strong>{returnTarget.productId}</strong>
                        {returnTarget.variant ? ` (${returnTarget.variant})` : ""} del pedido #{order.id}
                      </p>
                      <label>
                        <span>Cantidad (máx {returnTarget.maxQty})</span>
                        <input
                          type="number"
                          min={1}
                          max={returnTarget.maxQty}
                          value={returnQty}
                          onChange={(e) => setReturnQty(e.target.value)}
                        />
                      </label>
                      <label>
                        <span>Motivo</span>
                        <input value={returnMotivo} onChange={(e) => setReturnMotivo(e.target.value)} placeholder="¿Qué pasó con el producto?" />
                      </label>
                      {returnError ? <div className="error-box">{returnError}</div> : null}
                      <div className="admin-form-actions">
                        <button className="primary-btn" type="submit" disabled={returnSubmitting}>
                          {returnSubmitting ? "Enviando..." : "Confirmar devolución"}
                        </button>
                        <button className="secondary-btn" type="button" onClick={cancelReturn}>
                          Cancelar
                        </button>
                      </div>
                    </form>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </section>
      ) : null}

      {tab === "returns" ? (
        <section className="admin-section">
          {returnsLoading ? (
            <div className="empty-state">Cargando tus devoluciones...</div>
          ) : returns.length === 0 ? (
            <div className="empty-state">No solicitaste ninguna devolución todavía.</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th>Variante</th>
                    <th>Cantidad</th>
                    <th>Motivo</th>
                    <th>Solicitada</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {returns.map((r) => (
                    <tr key={r.id}>
                      <td>{r.productId}</td>
                      <td>{r.variant || "—"}</td>
                      <td>{r.cantidad}</td>
                      <td>{r.motivo}</td>
                      <td>{new Date(r.requestedAt).toLocaleString("es-AR")}</td>
                      <td>
                        <span className={`status-badge tone-${statusTone(r.estado)}`}>{r.estado}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
    </div>
  );
}
