import { PLACEHOLDER_IMAGE, handleImageError } from "../productImage";
import type { Category, Product, User } from "../types";

type Props = {
  categories: Category[];
  products: Product[];
  currentUser: User | null;
  onNavigate: (view: "home" | "products" | "offers" | "categories" | "cart" | "login") => void;
  onAddToCart: (product: Product) => void;
  onOpenProduct: (id: string) => void;
  wishlist: string[];
  onToggleWishlist: (productId: string) => void;
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

export function HomeScreen({
  categories,
  products,
  currentUser,
  onNavigate,
  onAddToCart,
  onOpenProduct,
  wishlist,
  onToggleWishlist,
}: Props) {
  const featured = products.slice(0, 4);

  return (
    <div className="page-shell">
      <section className="hero-card">
        <div className="hero-copy">
          <span className="eyebrow">Petshop • cuidado total</span>
          <h1>Todo para tu mascota, con confianza y estilo.</h1>
          <p>
            Alimentación, juguetes, accesorios y productos para cada etapa de tu compañero.
          </p>
          <div className="hero-actions">
            <button className="primary-btn" onClick={() => onNavigate("products")}>Ver productos</button>
            <button className="secondary-btn" onClick={() => onNavigate("offers")}>Ver ofertas</button>
          </div>
          <div className="hero-stats">
            <div>
              <strong>+12k</strong>
              <span>pedidos</span>
            </div>
            <div>
              <strong>4.9/5</strong>
              <span>reseñas</span>
            </div>
            <div>
              <strong>24h</strong>
              <span>envío</span>
            </div>
          </div>
        </div>
        <div className="hero-visual">
          <div className="pet-bubble">🐾</div>
          <img
            src="https://images.unsplash.com/photo-1517849845537-4d257902454a?auto=format&fit=crop&w=900&q=80"
            alt="Mascotas"
          />
        </div>
      </section>

      <section className="section-block">
        <div className="section-header">
          <h2>Categorías populares</h2>
          <button className="text-link" onClick={() => onNavigate("products")}>Ver todas</button>
        </div>
        <div className="category-grid">
          {categories.map((category) => (
            <button
              key={category.id}
              className="category-card"
              onClick={() => onNavigate("products")}
              style={{ borderColor: category.color || "#f2d2ab" }}
            >
              <span className="category-icon" style={{ background: category.color || "#ffe7c0" }}>
                {category.name.slice(0, 1).toUpperCase()}
              </span>
              <strong>{category.name}</strong>
              <small>Descubrir</small>
            </button>
          ))}
        </div>
      </section>

      <section className="section-block">
        <div className="section-header">
          <h2>Destacados</h2>
          <button className="text-link" onClick={() => onNavigate("products")}>Ver catálogo</button>
        </div>
        <div className="product-grid">
          {featured.map((product) => (
            <article key={product.id} className="product-card">
              <div className="product-image-wrap product-clickable" onClick={() => onOpenProduct(product.id)}>
                <img src={product.imageUrl || PLACEHOLDER_IMAGE} onError={handleImageError} alt={product.name} />
                {product.badge && <span className="product-badge">{product.badge}</span>}
                {currentUser?.role !== "ADMIN" ? (
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
                  <span>{product.stock} disponibles</span>
                </div>
                <div className="price-row">
                  <strong>{formatMoney(product.price)}</strong>
                  {product.oldPrice ? <span>{formatMoney(product.oldPrice)}</span> : null}
                </div>
                {currentUser?.role !== "ADMIN" ? (
                  <button className="primary-btn block" onClick={() => onAddToCart(product)} disabled={product.stock <= 0}>
                    {product.stock <= 0 ? "Sin stock" : product.hasVariants ? "Ver opciones" : "Agregar al carrito"}
                  </button>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="welcome-banner">
        <div>
          <span>¿Ya sos cliente?</span>
          <h3>{currentUser ? `Hola, ${currentUser.name}` : "Iniciá sesión para ver tu historial y carrito"}</h3>
        </div>
        <button className="secondary-btn" onClick={() => onNavigate(currentUser ? "cart" : "login")}>
          {currentUser ? "Ir al carrito" : "Ingresar"}
        </button>
      </section>
    </div>
  );
}
