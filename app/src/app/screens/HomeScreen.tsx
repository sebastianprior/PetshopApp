import type { SyntheticEvent } from "react";
import { Icon, categoryIcon } from "../components/Icon";
import { ProductCard } from "../components/ProductCard";
import type { Category, Product, RouletteState, User } from "../types";
import { SalchiAvatar } from "./SalchiAvatar";

type Props = {
  categories: Category[];
  products: Product[];
  currentUser: User | null;
  onNavigate: (view: "home" | "products" | "offers" | "categories" | "cart" | "login") => void;
  onAddToCart: (product: Product) => void;
  onOpenProduct: (id: string) => void;
  wishlist: string[];
  onToggleWishlist: (productId: string) => void;
  roulette?: RouletteState | null;
  onOpenRoulette?: () => void;
  /** Abre el catálogo ya filtrado por la categoría elegida. */
  onSelectCategory?: (categoryId: string) => void;
};

const photo = (id: string, width: number) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${width}&q=80`;

// Si una foto no carga (sin conexión, bloqueada) se oculta y queda el fondo de color de la tarjeta.
const hideBrokenPhoto = (event: SyntheticEvent<HTMLImageElement>) => {
  event.currentTarget.style.visibility = "hidden";
};

const BENEFITS = [
  { icon: "truck", title: "Envíos a todo el país", text: "Recibí tu pedido en la puerta de casa." },
  { icon: "card", title: "Cuotas sin interés", text: "Pagá como te quede más cómodo." },
  { icon: "shield", title: "Compra segura", text: "Tus datos y pagos siempre protegidos." },
  { icon: "headset", title: "Atención cercana", text: "Salchi Bot te ayuda a elegir, todos los días." },
] as const;

export function HomeScreen({
  categories,
  products,
  currentUser,
  onNavigate,
  onAddToCart,
  onOpenProduct,
  wishlist,
  onToggleWishlist,
  roulette = null,
  onOpenRoulette,
  onSelectCategory,
}: Props) {
  const isAdmin = currentUser?.role === "ADMIN";
  const featured = products.slice(0, 4);
  const promoted = products.filter((product) => product.precioPromocional != null).slice(0, 4);
  const selectCategory = (categoryId: string) => (onSelectCategory ? onSelectCategory(categoryId) : onNavigate("products"));

  const productCard = (product: Product) => (
    <ProductCard
      key={product.id}
      product={product}
      isAdmin={isAdmin}
      wishlist={wishlist}
      onToggleWishlist={onToggleWishlist}
      onAddToCart={onAddToCart}
      onOpenProduct={onOpenProduct}
    />
  );

  return (
    <div className="page-shell">
      <section className="hero-card">
        <div className="hero-copy">
          <span className="eyebrow">
            <Icon name="paw" size={14} /> Petshop • cuidado total
          </span>
          <h1>
            Todo para tu mascota, <span className="hero-highlight">con confianza y estilo.</span>
          </h1>
          <p>Alimentación, juguetes, accesorios y productos para cada etapa de tu compañero.</p>
          <div className="hero-actions">
            <button className="primary-btn" onClick={() => onNavigate("products")}>
              Ver productos <Icon name="arrow-right" size={18} />
            </button>
            <button className="secondary-btn" onClick={() => onNavigate("offers")}>
              Ver ofertas
            </button>
          </div>
        </div>
        <div className="hero-visual" aria-hidden="true">
          <div className="hero-photo hero-photo-main">
            <img src={photo("1598875706250-21faaf804361", 900)} alt="" onError={hideBrokenPhoto} />
          </div>
          <div className="hero-photo hero-photo-side">
            <img src={photo("1536589961747-e239b2abbec2", 600)} alt="" onError={hideBrokenPhoto} />
          </div>
          <span className="pet-bubble">
            <Icon name="paw" size={26} />
          </span>
          <span className="hero-sticker">
            <Icon name="heart" size={16} filled /> Hecho con amor por las mascotas
          </span>
        </div>
      </section>

      <section className="benefits-strip" aria-label="Beneficios">
        {BENEFITS.map((benefit) => (
          <div key={benefit.title} className="benefit">
            <span className="benefit-icon">
              <Icon name={benefit.icon} size={24} />
            </span>
            <div>
              <strong>{benefit.title}</strong>
              <small>{benefit.text}</small>
            </div>
          </div>
        ))}
      </section>

      {roulette && onOpenRoulette ? (
        <section className="roulette-banner">
          <SalchiAvatar headset={false} className="roulette-banner-dog" />
          <div className="roulette-banner-text">
            <strong>Descuento del día</strong>
            <span>
              {roulette.spun
                ? `Hoy te tocó ${roulette.percent}% OFF en ${roulette.categoryName}`
                : `Girá la ruleta y ganá ${roulette.percent}% OFF en una categoría`}
            </span>
          </div>
          <button className="primary-btn" onClick={onOpenRoulette}>
            {roulette.spun ? "Ver mi descuento" : "¡Girar!"}
          </button>
        </section>
      ) : null}

      <section className="section-block">
        <div className="section-header">
          <h2>Categorías populares</h2>
          <button className="text-link" onClick={() => onNavigate("products")}>
            Ver todas
          </button>
        </div>
        <div className="category-grid">
          {categories.map((category, index) => (
            <button
              key={category.id}
              className={`category-card tone-${index % 4}`}
              onClick={() => selectCategory(category.id)}
            >
              <span className="category-icon">
                <Icon name={categoryIcon(category.id)} size={30} />
              </span>
              <strong>{category.name}</strong>
              <small>Descubrir</small>
            </button>
          ))}
        </div>
      </section>

      <section className="pet-banners" aria-label="Comprar por mascota">
        <button className="pet-banner pet-banner-dog" onClick={() => selectCategory("perros")}>
          <img src={photo("1537204696486-967f1b7198c8", 800)} alt="" loading="lazy" onError={hideBrokenPhoto} />
          <span className="pet-banner-copy">
            <small>Para ellos</small>
            <strong>Perros</strong>
            <span className="pet-banner-cta">
              Ver productos <Icon name="arrow-right" size={16} />
            </span>
          </span>
        </button>
        <button className="pet-banner pet-banner-cat" onClick={() => selectCategory("gatos")}>
          <img src={photo("1583795128727-6ec3642408f8", 800)} alt="" loading="lazy" onError={hideBrokenPhoto} />
          <span className="pet-banner-copy">
            <small>Para ellos</small>
            <strong>Gatos</strong>
            <span className="pet-banner-cta">
              Ver productos <Icon name="arrow-right" size={16} />
            </span>
          </span>
        </button>
      </section>

      {promoted.length > 0 ? (
        <section className="section-block">
          <div className="section-header">
            <h2>Ofertas de la semana</h2>
            <button className="text-link" onClick={() => onNavigate("offers")}>
              Ver todas las ofertas
            </button>
          </div>
          <div className="product-grid">{promoted.map(productCard)}</div>
        </section>
      ) : null}

      <section className="section-block">
        <div className="section-header">
          <h2>Destacados</h2>
          <button className="text-link" onClick={() => onNavigate("products")}>
            Ver catálogo
          </button>
        </div>
        <div className="product-grid">{featured.map(productCard)}</div>
      </section>

      <section className="welcome-banner">
        <div>
          <span>{currentUser ? "¡Qué bueno verte de nuevo!" : "¿Ya sos cliente?"}</span>
          <h3>{currentUser ? `Hola, ${currentUser.name}` : "Iniciá sesión para ver tu historial y carrito"}</h3>
        </div>
        <button className="secondary-btn" onClick={() => onNavigate(currentUser ? "cart" : "login")}>
          {currentUser ? "Ir al carrito" : "Ingresar"}
        </button>
      </section>
    </div>
  );
}
