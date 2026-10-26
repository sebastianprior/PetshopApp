import { Icon } from "../components/Icon";
import { ProductCard } from "../components/ProductCard";
import { discountOf, isOffer } from "../components/offers";
import type { Product } from "../types";

type Props = {
  products: Product[];
  onAddToCart: (product: Product) => void;
  onOpenProduct: (id: string) => void;
  isAdmin: boolean;
  wishlist: string[];
  onToggleWishlist: (productId: string) => void;
};

export function OffersScreen({ products, onAddToCart, onOpenProduct, isAdmin, wishlist, onToggleWishlist }: Props) {
  const offers = products.filter(isOffer);
  const bestDiscount = offers.reduce((best, product) => Math.max(best, discountOf(product)), 0);

  return (
    <div className="page-shell">
      <section className="offers-hero">
        <div>
          <span className="eyebrow">Ofertas exclusivas</span>
          <h1>Ahorrá en productos para toda la familia.</h1>
          <p>Promociones reales, descuentos por temporada y artículos seleccionados para tu mascota.</p>
        </div>
        {bestDiscount > 0 ? (
          <div className="offers-pill-wrap">
            <span className="offers-pill">
              <Icon name="tag" size={20} /> Hasta {bestDiscount}% OFF
            </span>
          </div>
        ) : null}
      </section>

      {offers.length === 0 ? (
        <div className="empty-state">
          <Icon name="gift" size={36} />
          <strong>Todavía no hay ofertas activas, pero pronto agregamos más promos.</strong>
        </div>
      ) : (
        <div className="product-grid">
          {offers.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              isAdmin={isAdmin}
              wishlist={wishlist}
              onToggleWishlist={onToggleWishlist}
              onAddToCart={onAddToCart}
              onOpenProduct={onOpenProduct}
            />
          ))}
        </div>
      )}
    </div>
  );
}
