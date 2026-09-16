import type { Product } from "../types";

type Props = {
  product: Product | null;
  onBack: () => void;
  onAddToCart: (product: Product) => void;
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

export function ProductDetailScreen({ product, onBack, onAddToCart }: Props) {
  if (!product) {
    return (
      <div className="page-shell">
        <div className="empty-state">No se encontró el producto.</div>
      </div>
    );
  }

  return (
    <div className="page-shell product-detail-shell">
      <button className="secondary-btn" onClick={onBack}>← Volver</button>

      <div className="product-detail">
        <div className="product-detail-image">
          <img
            src={product.imageUrl || "https://images.unsplash.com/photo-1583337130417-3346a1be7dee?auto=format&fit=crop&w=900&q=80"}
            alt={product.name}
          />
          {product.badge ? <span className="product-badge">{product.badge}</span> : null}
        </div>

        <div className="product-detail-info">
          <span className="eyebrow">{product.categoryId}</span>
          <h1>{product.name}</h1>
          <p className="product-detail-brand">{product.brand}</p>

          <div className="rating-row">
            <span>⭐ {product.rating.toFixed(1)}</span>
            <span>{product.stock} disponibles</span>
          </div>

          <div className="price-row detail-price-row">
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

          <p className="product-description">
            Producto pensado para cuidar a tu mascota con comodidad, estilo y calidad. Ideal para
            diario, entretenimiento o una rutina de cuidado más completa.
          </p>

          <div className="product-detail-actions">
            <button className="primary-btn" onClick={() => onAddToCart(product)}>
              Agregar al carrito
            </button>
          </div>

          <div className="detail-specs">
            <div>
              <span>Envío</span>
              <strong>24 hs</strong>
            </div>
            <div>
              <span>Pago</span>
              <strong>6 cuotas</strong>
            </div>
            <div>
              <span>Garantía</span>
              <strong>30 días</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
