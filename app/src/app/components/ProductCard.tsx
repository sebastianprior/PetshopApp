import { PLACEHOLDER_IMAGE, handleImageError } from "../productImage";
import type { Product } from "../types";
import { Icon } from "./Icon";

export const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

type Props = {
  product: Product;
  isAdmin: boolean;
  wishlist: string[];
  onToggleWishlist: (productId: string) => void;
  onAddToCart: (product: Product) => void;
  onOpenProduct: (id: string) => void;
  /** Etiqueta extra sobre la foto (por ejemplo, el descuento de la ruleta del día). */
  extraTag?: string | null;
};

/** Tarjeta de producto única para Inicio, Catálogo, Ofertas y Favoritos. */
export function ProductCard({ product, isAdmin, wishlist, onToggleWishlist, onAddToCart, onOpenProduct, extraTag = null }: Props) {
  const favorite = wishlist.includes(product.id);
  const hasPromo = product.precioPromocional != null;
  const currentPrice = hasPromo ? (product.precioPromocional as number) : product.price;
  const previousPrice = hasPromo ? product.price : product.oldPrice && product.oldPrice > product.price ? product.oldPrice : null;
  const discount = previousPrice ? Math.round((1 - currentPrice / previousPrice) * 100) : 0;
  const soldOut = product.stock <= 0;
  const lowStock = !soldOut && product.stock <= 5;

  return (
    <article className="product-card">
      <div className="product-image-wrap product-clickable" onClick={() => onOpenProduct(product.id)}>
        <img src={product.imageUrl || PLACEHOLDER_IMAGE} onError={handleImageError} alt={product.name} loading="lazy" />
        <div className="product-tags">
          {product.badge ? <span className="product-badge">{product.badge}</span> : null}
          {discount > 0 ? <span className="discount-tag">-{discount}%</span> : null}
          {extraTag ? <span className="roulette-tag">{extraTag}</span> : null}
        </div>
        {!isAdmin ? (
          <button
            className={favorite ? "wishlist-heart active" : "wishlist-heart"}
            onClick={(e) => {
              e.stopPropagation();
              onToggleWishlist(product.id);
            }}
            aria-label={favorite ? "Quitar de favoritos" : "Agregar a favoritos"}
            aria-pressed={favorite}
          >
            <Icon name="heart" size={20} filled={favorite} />
          </button>
        ) : null}
      </div>
      <div className="product-body">
        <span className="brand">{product.brand}</span>
        <h3 onClick={() => onOpenProduct(product.id)} className="product-name-link">
          {product.name}
        </h3>
        <div className="rating-row">
          <span className="rating-score">
            <Icon name="star" size={15} filled /> {product.rating.toFixed(1)}
          </span>
          <span className={soldOut ? "stock-note out" : lowStock ? "stock-note low" : "stock-note"}>
            {soldOut ? "Agotado" : lowStock ? `¡Últimas ${product.stock}!` : `${product.stock} disponibles`}
          </span>
        </div>
        <div className="price-row">
          <strong>{formatMoney(currentPrice)}</strong>
          {previousPrice ? <span>{formatMoney(previousPrice)}</span> : null}
        </div>
        {hasPromo && product.tipoPromocion ? <span className="promo-tag">{product.tipoPromocion}</span> : null}
        {!isAdmin ? (
          <button className="primary-btn block" onClick={() => onAddToCart(product)} disabled={soldOut}>
            {soldOut ? "Sin stock" : product.hasVariants ? "Ver opciones" : "Agregar al carrito"}
          </button>
        ) : null}
      </div>
    </article>
  );
}

/** Grilla de tarjetas "esqueleto" mientras carga el catálogo. */
export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="product-grid" aria-busy="true" aria-live="polite">
      <span className="sr-only">Cargando productos...</span>
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="product-card skeleton-card" aria-hidden="true">
          <div className="product-image-wrap skeleton" />
          <div className="product-body">
            <div className="skeleton skeleton-line short" />
            <div className="skeleton skeleton-line" />
            <div className="skeleton skeleton-line short" />
            <div className="skeleton skeleton-btn" />
          </div>
        </div>
      ))}
    </div>
  );
}
