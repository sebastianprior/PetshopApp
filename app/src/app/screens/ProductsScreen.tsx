import type { Product } from "../types";

type Props = {
  products: Product[];
  loading: boolean;
  categoryFilter: string;
  sort: string;
  onCategoryChange: (value: string) => void;
  onSortChange: (value: string) => void;
  onAddToCart: (product: Product) => void;
  onOpenProduct: (id: string) => void;
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

export function ProductsScreen({
  products,
  loading,
  categoryFilter,
  sort,
  onCategoryChange,
  onSortChange,
  onAddToCart,
  onOpenProduct,
}: Props) {
  return (
    <div className="page-shell">
      <section className="toolbar-card">
        <div className="toolbar-row">
          <label>
            <span>Categoría</span>
            <select value={categoryFilter} onChange={(e) => onCategoryChange(e.target.value)}>
              <option value="all">Todas</option>
              <option value="alimentos">Alimentos</option>
              <option value="juguetes">Juguetes</option>
              <option value="accesorios">Accesorios</option>
              <option value="perros">Perros</option>
              <option value="gatos">Gatos</option>
            </select>
          </label>

          <label>
            <span>Ordenar</span>
            <select value={sort} onChange={(e) => onSortChange(e.target.value)}>
              <option value="">Relevancia</option>
              <option value="menorprecio">Menor precio</option>
              <option value="mayorprecio">Mayor precio</option>
            </select>
          </label>
        </div>
      </section>

      {loading ? (
        <div className="empty-state">Cargando productos...</div>
      ) : (
        <div className="product-grid">
          {products.map((product) => (
            <article key={product.id} className="product-card">
              <div className="product-image-wrap product-clickable" onClick={() => onOpenProduct(product.id)}>
                <img
                  src={product.imageUrl || "https://images.unsplash.com/photo-1583337130417-3346a1be7dee?auto=format&fit=crop&w=700&q=80"}
                  alt={product.name}
                />
                {product.badge ? <span className="product-badge">{product.badge}</span> : null}
              </div>
              <div className="product-body">
                <span className="brand">{product.brand}</span>
                <h3 onClick={() => onOpenProduct(product.id)} className="product-name-link">{product.name}</h3>
                <div className="rating-row">
                  <span>⭐ {product.rating.toFixed(1)}</span>
                  <span>{product.stock} unidades</span>
                </div>
                <div className="price-row">
                  {product.precioPromocional != null ? (
                    <>
                      <strong>{formatMoney(product.precioPromocional)}</strong>
                      <span>{formatMoney(product.price)}</span>
                    </>
                  ) : (
                    <>
                      <strong>{formatMoney(product.price)}</strong>
                      {product.oldPrice ? <span>{formatMoney(product.oldPrice)}</span> : null}
                    </>
                  )}
                </div>
                {product.precioPromocional != null && product.tipoPromocion ? (
                  <span className="promo-tag">{product.tipoPromocion}</span>
                ) : null}
                <button className="primary-btn block" onClick={() => onAddToCart(product)}>
                  Agregar al carrito
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
