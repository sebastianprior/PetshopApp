import { Icon } from "../components/Icon";
import { ProductCard } from "../components/ProductCard";
import type { Product } from "../types";

type Props = {
  products: Product[];
  wishlist: string[];
  onAddToCart: (product: Product) => void;
  onOpenProduct: (id: string) => void;
  onToggleWishlist: (productId: string) => void;
};

export function WishlistScreen({ products, wishlist, onAddToCart, onOpenProduct, onToggleWishlist }: Props) {
  const favorites = products.filter((product) => wishlist.includes(product.id));

  return (
    <div className="page-shell">
      <div className="section-header">
        <h2>Mis favoritos</h2>
      </div>

      {favorites.length === 0 ? (
        <div className="empty-state">
          <Icon name="heart" size={36} />
          <strong>Todavía no agregaste productos a favoritos.</strong>
        </div>
      ) : (
        <div className="product-grid">
          {favorites.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              isAdmin={false}
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
