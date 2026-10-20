import { PLACEHOLDER_IMAGE, handleImageError } from "../productImage";
import { useEffect, useState } from "react";
import type { Product } from "../types";

const PAGE_SIZE = 12;

type Props = {
  products: Product[];
  loading: boolean;
  categoryFilter: string;
  sort: string;
  search: string;
  onCategoryChange: (value: string) => void;
  onSortChange: (value: string) => void;
  onSearchChange: (value: string) => void;
  onAddToCart: (product: Product) => void;
  onOpenProduct: (id: string) => void;
  isAdmin: boolean;
  wishlist: string[];
  onToggleWishlist: (productId: string) => void;
  rouletteCategoryId?: string | null;
  roulettePercent?: number;
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

export function ProductsScreen({
  products,
  loading,
  categoryFilter,
  sort,
  search,
  onCategoryChange,
  onSortChange,
  onSearchChange,
  onAddToCart,
  onOpenProduct,
  isAdmin,
  wishlist,
  onToggleWishlist,
  rouletteCategoryId = null,
  roulettePercent = 10,
}: Props) {
  const [page, setPage] = useState(1);

  useEffect(() => {
    setPage(1);
  }, [products, categoryFilter, sort, search]);

  const totalPages = Math.max(1, Math.ceil(products.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const paginatedProducts = products.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  return (
    <div className="page-shell">
      <section className="toolbar-card">
        <div className="toolbar-row">
          <label className="toolbar-search">
            <span>Buscar</span>
            <input
              value={search}
              onChange={(e) => onSearchChange(e.target.value)}
              placeholder="Nombre o marca..."
            />
          </label>

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
      ) : products.length === 0 ? (
        <div className="empty-state">No encontramos productos con esos filtros.</div>
      ) : (
        <div className="product-grid">
          {paginatedProducts.map((product) => (
            <article key={product.id} className="product-card">
              <div className="product-image-wrap product-clickable" onClick={() => onOpenProduct(product.id)}>
                <img
                  src={product.imageUrl || PLACEHOLDER_IMAGE}
                  onError={handleImageError}
                  alt={product.name}
                />
                {product.badge ? <span className="product-badge">{product.badge}</span> : null}
                {rouletteCategoryId && product.categoryId === rouletteCategoryId ? (
                  <span className="roulette-tag">🎡 -{roulettePercent}% hoy</span>
                ) : null}
                {!isAdmin ? (
                  <button
                    className={wishlist.includes(product.id) ? "wishlist-heart active" : "wishlist-heart"}
                    onClick={(e) => {
                      e.stopPropagation();
                      onToggleWishlist(product.id);
                    }}
                    aria-label={wishlist.includes(product.id) ? "Quitar de favoritos" : "Agregar a favoritos"}
                  >
                    ♥
                  </button>
                ) : null}
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
                {!isAdmin ? (
                  <button className="primary-btn block" onClick={() => onAddToCart(product)} disabled={product.stock <= 0}>
                    {product.stock <= 0 ? "Sin stock" : product.hasVariants ? "Ver opciones" : "Agregar al carrito"}
                  </button>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      )}

      {!loading && totalPages > 1 ? (
        <div className="pagination-row">
          <button className="secondary-btn" onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={currentPage <= 1}>
            ← Anterior
          </button>
          <span>
            Página {currentPage} de {totalPages}
          </span>
          <button className="secondary-btn" onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={currentPage >= totalPages}>
            Siguiente →
          </button>
        </div>
      ) : null}
    </div>
  );
}
