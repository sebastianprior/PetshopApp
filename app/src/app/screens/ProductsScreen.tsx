import { useEffect, useState } from "react";
import { Icon, categoryIcon } from "../components/Icon";
import { ProductCard, ProductGridSkeleton } from "../components/ProductCard";
import type { Category, Product } from "../types";

const PAGE_SIZE = 12;

// Categorías que se muestran si todavía no llegaron las del servidor
const FALLBACK_CATEGORIES: Category[] = [
  { id: "alimentos", name: "Alimentos" },
  { id: "juguetes", name: "Juguetes" },
  { id: "accesorios", name: "Accesorios" },
  { id: "perros", name: "Perros" },
  { id: "gatos", name: "Gatos" },
];

type Props = {
  products: Product[];
  loading: boolean;
  categoryFilter: string;
  sort: string;
  search: string;
  categories?: Category[];
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

export function ProductsScreen({
  products,
  loading,
  categoryFilter,
  sort,
  search,
  categories,
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

  const chips = categories && categories.length > 0 ? categories : FALLBACK_CATEGORIES;
  const activeCategory = chips.find((category) => category.id === categoryFilter);
  const totalPages = Math.max(1, Math.ceil(products.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const paginatedProducts = products.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  return (
    <div className="page-shell">
      <header className="catalog-header">
        <span className="eyebrow">Catálogo</span>
        <h1>{activeCategory ? activeCategory.name : "Todos los productos"}</h1>
        <p>
          {loading
            ? "Buscando lo mejor para tu mascota..."
            : `${products.length} ${products.length === 1 ? "producto" : "productos"} para elegir`}
        </p>
      </header>

      <section className="toolbar-card">
        <div className="toolbar-row">
          <label className="toolbar-search">
            <span>Buscar</span>
            <span className="input-with-icon">
              <Icon name="search" size={18} />
              <input
                value={search}
                onChange={(e) => onSearchChange(e.target.value)}
                placeholder="Nombre o marca..."
              />
            </span>
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

        <div className="chip-row" role="group" aria-label="Filtrar por categoría">
          <button
            type="button"
            className={categoryFilter === "all" ? "chip active" : "chip"}
            aria-pressed={categoryFilter === "all"}
            onClick={() => onCategoryChange("all")}
          >
            <Icon name="paw" size={16} /> Todas
          </button>
          {chips.map((category) => (
            <button
              key={category.id}
              type="button"
              className={categoryFilter === category.id ? "chip active" : "chip"}
              aria-pressed={categoryFilter === category.id}
              onClick={() => onCategoryChange(category.id)}
            >
              <Icon name={categoryIcon(category.id)} size={16} /> {category.name}
            </button>
          ))}
        </div>
      </section>

      {loading ? (
        <ProductGridSkeleton />
      ) : products.length === 0 ? (
        <div className="empty-state">
          <Icon name="search" size={36} />
          <strong>No encontramos productos con esos filtros.</strong>
          <button
            className="secondary-btn"
            onClick={() => {
              onSearchChange("");
              onCategoryChange("all");
            }}
          >
            Limpiar filtros
          </button>
        </div>
      ) : (
        <div className="product-grid">
          {paginatedProducts.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              isAdmin={isAdmin}
              wishlist={wishlist}
              onToggleWishlist={onToggleWishlist}
              onAddToCart={onAddToCart}
              onOpenProduct={onOpenProduct}
              extraTag={rouletteCategoryId && product.categoryId === rouletteCategoryId ? `-${roulettePercent}% hoy` : null}
            />
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
