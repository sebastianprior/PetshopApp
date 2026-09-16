import { useEffect, useState } from "react";
import type { Category, OrderRecord, Product, ReturnRecord } from "../types";
import {
  createProduct,
  deleteProduct,
  fetchAllOrders,
  fetchAllReturns,
  fetchProducts,
  updateOrderStatus,
  updateProduct,
  updateReturnStatus,
} from "../api";

type Props = {
  authToken: string;
  categories: Category[];
};

type Tab = "orders" | "products" | "returns";

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

const EMPTY_PRODUCT_FORM = {
  id: "",
  name: "",
  brand: "",
  price: "",
  oldPrice: "",
  rating: "",
  imageUrl: "",
  badge: "",
  categoryId: "",
  stock: "",
};

export function AdminDashboardScreen({ authToken, categories }: Props) {
  const [tab, setTab] = useState<Tab>("orders");

  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [orderStatusFilter, setOrderStatusFilter] = useState("");

  const [products, setProducts] = useState<Product[]>([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productForm, setProductForm] = useState(EMPTY_PRODUCT_FORM);
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [productError, setProductError] = useState<string | null>(null);

  const [returns, setReturns] = useState<ReturnRecord[]>([]);
  const [returnsLoading, setReturnsLoading] = useState(false);

  const [actionError, setActionError] = useState<string | null>(null);

  const loadOrders = async () => {
    setOrdersLoading(true);
    try {
      const data = await fetchAllOrders(authToken);
      setOrders(data);
    } catch (error) {
      console.error("No se pudieron cargar las órdenes", error);
    } finally {
      setOrdersLoading(false);
    }
  };

  const loadProducts = async () => {
    setProductsLoading(true);
    try {
      const data = await fetchProducts();
      setProducts(data);
    } catch (error) {
      console.error("No se pudieron cargar los productos", error);
    } finally {
      setProductsLoading(false);
    }
  };

  const loadReturns = async () => {
    setReturnsLoading(true);
    try {
      const data = await fetchAllReturns(authToken);
      setReturns(data);
    } catch (error) {
      console.error("No se pudieron cargar las devoluciones", error);
    } finally {
      setReturnsLoading(false);
    }
  };

  useEffect(() => {
    void loadOrders();
    void loadProducts();
    void loadReturns();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleOrderStatusChange = async (order: OrderRecord, estado: string) => {
    setActionError(null);
    try {
      const updated = await updateOrderStatus(authToken, order.id, estado);
      setOrders((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo cambiar el estado de la orden");
    }
  };

  const filteredOrders = orderStatusFilter
    ? orders.filter((order) => order.estado === orderStatusFilter)
    : orders;

  const orderStatuses = Array.from(new Set(orders.map((o) => o.estado)));

  const startEditProduct = (product: Product) => {
    setEditingProductId(product.id);
    setProductForm({
      id: product.id,
      name: product.name,
      brand: product.brand,
      price: String(product.price),
      oldPrice: product.oldPrice != null ? String(product.oldPrice) : "",
      rating: String(product.rating),
      imageUrl: product.imageUrl || "",
      badge: product.badge || "",
      categoryId: product.categoryId,
      stock: String(product.stock),
    });
    setProductError(null);
  };

  const resetProductForm = () => {
    setEditingProductId(null);
    setProductForm(EMPTY_PRODUCT_FORM);
    setProductError(null);
  };

  const handleProductSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setProductError(null);

    if (!productForm.name.trim() || !productForm.price || !productForm.categoryId) {
      setProductError("Completa al menos nombre, precio y categoría.");
      return;
    }

    const payload: Partial<Product> = {
      id: productForm.id.trim() || undefined,
      name: productForm.name.trim(),
      brand: productForm.brand.trim(),
      price: Number(productForm.price),
      oldPrice: productForm.oldPrice ? Number(productForm.oldPrice) : null,
      rating: productForm.rating ? Number(productForm.rating) : 0,
      imageUrl: productForm.imageUrl.trim(),
      badge: productForm.badge.trim() || undefined,
      categoryId: productForm.categoryId,
      stock: productForm.stock ? Number(productForm.stock) : 0,
    };

    try {
      if (editingProductId) {
        await updateProduct(authToken, editingProductId, payload);
      } else {
        await createProduct(authToken, payload);
      }
      resetProductForm();
      await loadProducts();
    } catch (error) {
      setProductError(error instanceof Error ? error.message : "No se pudo guardar el producto");
    }
  };

  const handleDeleteProduct = async (product: Product) => {
    setActionError(null);
    try {
      await deleteProduct(authToken, product.id);
      await loadProducts();
      if (editingProductId === product.id) {
        resetProductForm();
      }
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo eliminar el producto");
    }
  };

  const handleReturnDecision = async (devolucion: ReturnRecord, estado: "APROBADA" | "RECHAZADA") => {
    setActionError(null);
    try {
      const updated = await updateReturnStatus(authToken, devolucion.id, estado);
      setReturns((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "No se pudo actualizar la devolución");
    }
  };

  const pendingReturns = returns.filter((r) => r.estado === "PENDIENTE");

  return (
    <div className="page-shell admin-shell">
      <div className="section-header">
        <h2>Panel de administración</h2>
      </div>

      <div className="admin-tabs">
        <button className={tab === "orders" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("orders")}>
          Órdenes
        </button>
        <button className={tab === "products" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("products")}>
          Productos
        </button>
        <button className={tab === "returns" ? "admin-tab active" : "admin-tab"} onClick={() => setTab("returns")}>
          Devoluciones pendientes {pendingReturns.length > 0 ? `(${pendingReturns.length})` : ""}
        </button>
      </div>

      {actionError ? <div className="error-box">{actionError}</div> : null}

      {tab === "orders" ? (
        <section className="admin-section">
          <div className="toolbar-card">
            <div className="toolbar-row">
              <label>
                <span>Filtrar por estado</span>
                <select value={orderStatusFilter} onChange={(e) => setOrderStatusFilter(e.target.value)}>
                  <option value="">Todos</option>
                  {orderStatuses.map((estado) => (
                    <option key={estado} value={estado}>
                      {estado}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </div>

          {ordersLoading ? (
            <div className="empty-state">Cargando órdenes...</div>
          ) : filteredOrders.length === 0 ? (
            <div className="empty-state">No hay órdenes para mostrar.</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Usuario</th>
                    <th>Fecha</th>
                    <th>Items</th>
                    <th>Total</th>
                    <th>Estado</th>
                    <th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredOrders.map((order) => (
                    <tr key={order.id}>
                      <td>{order.id}</td>
                      <td className="mono">{order.userId}</td>
                      <td>{new Date(order.fecha).toLocaleString("es-AR")}</td>
                      <td>{order.items.reduce((sum, i) => sum + i.quantity, 0)}</td>
                      <td>{formatMoney(order.total)}</td>
                      <td>
                        <span className="status-badge">{order.estado}</span>
                      </td>
                      <td>
                        <select
                          value=""
                          onChange={(e) => {
                            if (e.target.value) void handleOrderStatusChange(order, e.target.value);
                          }}
                        >
                          <option value="">Cambiar estado...</option>
                          <option value="PENDIENTE">PENDIENTE</option>
                          <option value="COMPLETADA">COMPLETADA</option>
                          <option value="ENVIADA">ENVIADA</option>
                          <option value="CANCELADA">CANCELADA</option>
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}

      {tab === "products" ? (
        <section className="admin-section">
          <form className="admin-form-grid" onSubmit={handleProductSubmit}>
            <label>
              <span>Nombre</span>
              <input value={productForm.name} onChange={(e) => setProductForm({ ...productForm, name: e.target.value })} />
            </label>
            <label>
              <span>Marca</span>
              <input value={productForm.brand} onChange={(e) => setProductForm({ ...productForm, brand: e.target.value })} />
            </label>
            <label>
              <span>Precio</span>
              <input type="number" value={productForm.price} onChange={(e) => setProductForm({ ...productForm, price: e.target.value })} />
            </label>
            <label>
              <span>Precio anterior</span>
              <input type="number" value={productForm.oldPrice} onChange={(e) => setProductForm({ ...productForm, oldPrice: e.target.value })} />
            </label>
            <label>
              <span>Rating</span>
              <input type="number" step="0.1" value={productForm.rating} onChange={(e) => setProductForm({ ...productForm, rating: e.target.value })} />
            </label>
            <label>
              <span>Stock</span>
              <input type="number" value={productForm.stock} onChange={(e) => setProductForm({ ...productForm, stock: e.target.value })} />
            </label>
            <label>
              <span>Categoría</span>
              <select value={productForm.categoryId} onChange={(e) => setProductForm({ ...productForm, categoryId: e.target.value })}>
                <option value="">Seleccionar...</option>
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>Badge</span>
              <input value={productForm.badge} onChange={(e) => setProductForm({ ...productForm, badge: e.target.value })} />
            </label>
            <label className="admin-form-wide">
              <span>URL de imagen</span>
              <input value={productForm.imageUrl} onChange={(e) => setProductForm({ ...productForm, imageUrl: e.target.value })} />
            </label>

            {productError ? <div className="error-box admin-form-wide">{productError}</div> : null}

            <div className="admin-form-actions admin-form-wide">
              <button className="primary-btn" type="submit">
                {editingProductId ? "Guardar cambios" : "Crear producto"}
              </button>
              {editingProductId ? (
                <button className="secondary-btn" type="button" onClick={resetProductForm}>
                  Cancelar edición
                </button>
              ) : null}
            </div>
          </form>

          {productsLoading ? (
            <div className="empty-state">Cargando productos...</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Marca</th>
                    <th>Precio</th>
                    <th>Stock</th>
                    <th>Categoría</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((product) => (
                    <tr key={product.id}>
                      <td>{product.name}</td>
                      <td>{product.brand}</td>
                      <td>{formatMoney(product.price)}</td>
                      <td>{product.stock}</td>
                      <td>{product.categoryId}</td>
                      <td>
                        <div className="admin-row-actions">
                          <button className="secondary-btn" onClick={() => startEditProduct(product)}>
                            Editar
                          </button>
                          <button className="secondary-btn danger" onClick={() => handleDeleteProduct(product)}>
                            Eliminar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}

      {tab === "returns" ? (
        <section className="admin-section">
          {returnsLoading ? (
            <div className="empty-state">Cargando devoluciones...</div>
          ) : pendingReturns.length === 0 ? (
            <div className="empty-state">No hay devoluciones pendientes.</div>
          ) : (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Usuario</th>
                    <th>Producto</th>
                    <th>Cantidad</th>
                    <th>Motivo</th>
                    <th>Solicitada</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingReturns.map((devolucion) => (
                    <tr key={devolucion.id}>
                      <td>{devolucion.id}</td>
                      <td className="mono">{devolucion.userId}</td>
                      <td>{devolucion.productId}</td>
                      <td>{devolucion.cantidad}</td>
                      <td>{devolucion.motivo}</td>
                      <td>{new Date(devolucion.requestedAt).toLocaleString("es-AR")}</td>
                      <td>
                        <div className="admin-row-actions">
                          <button className="secondary-btn" onClick={() => handleReturnDecision(devolucion, "APROBADA")}>
                            Aprobar
                          </button>
                          <button className="secondary-btn danger" onClick={() => handleReturnDecision(devolucion, "RECHAZADA")}>
                            Rechazar
                          </button>
                        </div>
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
