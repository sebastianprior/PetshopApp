import { PLACEHOLDER_IMAGE, handleImageError } from "../productImage";
import type { Product } from "../types";

type Props = {
  products: Product[];
  wishlist: string[];
  onAddToCart: (product: Product) => void;
  onOpenProduct: (id: string) => void;
  onToggleWishlist: (productId: string) => void;
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

export function WishlistScreen({ products, wishlist, onAddToCart, onOpenProduct, onToggleWishlist }: Props) {
  const favorites = products.filter((product) => wishlist.includes(product.id));

  return (
    <div className="page-shell">
      <div className="section-header">
        <h2>Mis favoritos</h2>
      </div>

      {favorites.length === 0 ? (
        <div className="empty-state">Todavía no agregaste productos a favoritos.</div>
      ) : (
        <div className="product-grid">
          {favorites.map((product) => (
            <article key={product.id} className="product-card">
              <div className="product-image-wrap product-clickable" onClick={() => onOpenProduct(product.id)}>
                <img
                  src={product.imageUrl || PLACEHOLDER_IMAGE}
                  onError={handleImageError}
                  alt={product.name}
                />
                {product.badge ? <span className="product-badge">{product.badge}</span> : null}
                <button
                  className="wishlist-heart active"
                  onClick={(e) => {
                    e.stopPropagation();
                    onToggleWishlist(product.id);
                  }}
                  aria-label="Quitar de favoritos"
                >
                  ♥
                </button>
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
                <button className="primary-btn block" onClick={() => onAddToCart(product)} disabled={product.stock <= 0}>
                  {product.stock <= 0 ? "Sin stock" : product.hasVariants ? "Ver opciones" : "Agregar al carrito"}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
