import { PLACEHOLDER_IMAGE, handleImageError } from "../productImage";
import { useEffect, useState } from "react";
import type { Product, ProductVariant, Review } from "../types";
import { deleteReview, fetchProductReviews, fetchProductVariants, submitReview } from "../api";
import { Icon, categoryIcon } from "../components/Icon";
import { formatMoney } from "../components/ProductCard";

type Props = {
  product: Product | null;
  authToken: string | null;
  isAdmin: boolean;
  wishlist: string[];
  onToggleWishlist: (productId: string) => void;
  onBack: () => void;
  onAddToCart: (product: Product, variant?: ProductVariant, quantity?: number) => void;
};

const variantLabel = (variant: ProductVariant) =>
  [variant.talle ? `Talle ${variant.talle}` : null, variant.color].filter(Boolean).join(" / ");

const RATING_LABELS: Record<number, string> = { 5: "Excelente", 4: "Muy bueno", 3: "Bueno", 2: "Regular", 1: "Malo" };

/** Estrellas de solo lectura (de 1 a 5). */
function Stars({ value, size = 16 }: { value: number; size?: number }) {
  return (
    <span className="stars" role="img" aria-label={`${value} de 5 estrellas`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Icon key={n} name="star" size={size} filled={n <= Math.round(value)} className={n <= Math.round(value) ? "star on" : "star"} />
      ))}
    </span>
  );
}

export function ProductDetailScreen({
  product,
  authToken,
  isAdmin,
  wishlist,
  onToggleWishlist,
  onBack,
  onAddToCart,
}: Props) {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [reviewsLoading, setReviewsLoading] = useState(false);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);

  const [variants, setVariants] = useState<ProductVariant[]>([]);
  const [variantsLoading, setVariantsLoading] = useState(false);
  const [selectedVariantId, setSelectedVariantId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    if (!product) return;
    setReviewsLoading(true);
    fetchProductReviews(product.id)
      .then(setReviews)
      .catch((error) => console.error("No se pudieron cargar las reseñas", error))
      .finally(() => setReviewsLoading(false));
  }, [product?.id]);

  useEffect(() => {
    setSelectedVariantId(null);
    setQuantity(1);
    if (!product || !product.hasVariants) {
      setVariants([]);
      return;
    }
    setVariantsLoading(true);
    fetchProductVariants(product.id)
      .then(setVariants)
      .catch((error) => console.error("No se pudieron cargar las variantes", error))
      .finally(() => setVariantsLoading(false));
  }, [product?.id, product?.hasVariants]);

  if (!product) {
    return (
      <div className="page-shell">
        <div className="empty-state">No se encontró el producto.</div>
      </div>
    );
  }

  const averageRating = reviews.length > 0 ? reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length : null;

  const selectedVariant = variants.find((v) => v.id === selectedVariantId);
  const displayImageUrl = selectedVariant?.imageUrl || product.imageUrl;
  const favorite = wishlist.includes(product.id);

  const hasPromo = product.precioPromocional != null;
  const currentPrice = hasPromo ? (product.precioPromocional as number) : product.price;
  const previousPrice = hasPromo ? product.price : product.oldPrice && product.oldPrice > product.price ? product.oldPrice : null;
  const discount = previousPrice ? Math.round((1 - currentPrice / previousPrice) * 100) : 0;

  // Máximo que se puede pedir de una vez: el stock de la variante elegida o, si no hay variantes, el del producto
  const maxQuantity = Math.max(1, selectedVariant ? selectedVariant.stock : product.stock);

  const handleSubmitReview = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!authToken) return;
    setReviewError(null);
    if (!comment.trim()) {
      setReviewError("Contanos algo sobre tu experiencia con el producto.");
      return;
    }

    setSubmitting(true);
    try {
      const created = await submitReview(authToken, product.id, rating, comment.trim());
      setReviews((prev) => [created, ...prev]);
      setComment("");
      setRating(5);
    } catch (error) {
      setReviewError(error instanceof Error ? error.message : "No se pudo enviar la reseña");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteReview = async (reviewId: number) => {
    if (!authToken) return;
    try {
      await deleteReview(authToken, product.id, reviewId);
      setReviews((prev) => prev.filter((r) => r.id !== reviewId));
    } catch (error) {
      console.error("No se pudo eliminar la reseña", error);
    }
  };

  return (
    <div className="page-shell product-detail-shell">
      <button className="secondary-btn back-btn" onClick={onBack}>← Volver</button>

      <div className="product-detail">
        <div className="product-detail-image">
          <img
            src={displayImageUrl || PLACEHOLDER_IMAGE}
            onError={handleImageError}
            alt={product.name}
          />
          <div className="product-tags">
            {product.badge ? <span className="product-badge">{product.badge}</span> : null}
            {discount > 0 ? <span className="discount-tag">-{discount}%</span> : null}
          </div>
          {!isAdmin ? (
            <button
              className={favorite ? "wishlist-heart active" : "wishlist-heart"}
              onClick={() => onToggleWishlist(product.id)}
              aria-label={favorite ? "Quitar de favoritos" : "Agregar a favoritos"}
              aria-pressed={favorite}
            >
              <Icon name="heart" size={22} filled={favorite} />
            </button>
          ) : null}
        </div>

        <div className="product-detail-info">
          <span className="eyebrow">
            <Icon name={categoryIcon(product.categoryId)} size={14} /> {product.categoryId}
          </span>
          <h1>{product.name}</h1>
          <p className="product-detail-brand">{product.brand}</p>

          <div className="rating-row">
            <span className="rating-score">
              <Stars value={product.rating} /> {product.rating.toFixed(1)}
            </span>
            <span className={product.stock <= 0 ? "stock-note out" : product.stock <= 5 ? "stock-note low" : "stock-note"}>
              {product.stock <= 0 ? "Agotado" : `${product.stock} disponibles`}
            </span>
          </div>

          <div className="price-row detail-price-row">
            <strong>{formatMoney(currentPrice)}</strong>
            {previousPrice ? <span>{formatMoney(previousPrice)}</span> : null}
          </div>
          {hasPromo && product.tipoPromocion ? <span className="promo-tag">{product.tipoPromocion}</span> : null}

          <p className="product-description">
            Producto pensado para cuidar a tu mascota con comodidad, estilo y calidad. Ideal para
            diario, entretenimiento o una rutina de cuidado más completa.
          </p>

          {isAdmin ? null : (
            <>
              {product.hasVariants ? (
                <div className="variant-picker">
                  <span>Elegí una opción</span>
                  {variantsLoading ? (
                    <div className="empty-state">Cargando opciones...</div>
                  ) : variants.length === 0 ? (
                    <div className="empty-state">No hay opciones disponibles para este producto.</div>
                  ) : (
                    <div className="variant-chip-row">
                      {variants.map((variant) => (
                        <button
                          key={variant.id}
                          type="button"
                          className={variant.id === selectedVariantId ? "variant-chip active" : "variant-chip"}
                          onClick={() => {
                            setSelectedVariantId(variant.id);
                            setQuantity(1);
                          }}
                          disabled={variant.stock <= 0}
                        >
                          {variantLabel(variant)}
                          <small>{variant.stock <= 0 ? "Sin stock" : `${variant.stock} disponibles`}</small>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              ) : null}

              <div className="product-detail-actions">
                <div className="qty-stepper" role="group" aria-label="Cantidad">
                  <button
                    type="button"
                    onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                    disabled={quantity <= 1}
                    aria-label="Menos unidades"
                  >
                    <Icon name="minus" size={16} />
                  </button>
                  <span aria-live="polite">{quantity}</span>
                  <button
                    type="button"
                    onClick={() => setQuantity((q) => Math.min(maxQuantity, q + 1))}
                    disabled={quantity >= maxQuantity}
                    aria-label="Más unidades"
                  >
                    <Icon name="plus" size={16} />
                  </button>
                </div>
                <button
                  className="primary-btn"
                  onClick={() => onAddToCart(product, selectedVariant, quantity)}
                  disabled={
                    product.stock <= 0 ||
                    (product.hasVariants && (variants.length === 0 || selectedVariantId == null))
                  }
                >
                  {product.stock <= 0
                    ? "Sin stock"
                    : product.hasVariants && selectedVariantId == null
                      ? "Elegí una opción"
                      : "Agregar al carrito"}
                </button>
              </div>
            </>
          )}

          <div className="detail-specs">
            <div>
              <Icon name="truck" size={22} />
              <span>Envío</span>
              <strong>24 hs</strong>
            </div>
            <div>
              <Icon name="card" size={22} />
              <span>Pago</span>
              <strong>6 cuotas</strong>
            </div>
            <div>
              <Icon name="shield" size={22} />
              <span>Garantía</span>
              <strong>30 días</strong>
            </div>
          </div>
        </div>
      </div>

      <section className="reviews-section">
        <div className="section-header">
          <h2>Reseñas{averageRating != null ? ` — ${averageRating.toFixed(1)} (${reviews.length})` : ""}</h2>
          {averageRating != null ? <Stars value={averageRating} size={20} /> : null}
        </div>

        {authToken ? (
          <form className="review-form" onSubmit={handleSubmitReview}>
            <div className="review-rating" role="radiogroup" aria-label="Tu puntaje">
              <span>Tu puntaje</span>
              <div className="rating-picker">
                {[1, 2, 3, 4, 5].map((n) => (
                  <button
                    key={n}
                    type="button"
                    role="radio"
                    aria-checked={rating === n}
                    aria-label={`${n} ${n === 1 ? "estrella" : "estrellas"}`}
                    className={n <= rating ? "star-btn on" : "star-btn"}
                    onClick={() => setRating(n)}
                  >
                    <Icon name="star" size={26} filled={n <= rating} />
                  </button>
                ))}
                <small>{RATING_LABELS[rating]}</small>
              </div>
            </div>
            <label>
              <span>Comentario</span>
              <input value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Contanos tu experiencia con el producto" />
            </label>
            {reviewError ? <div className="error-box">{reviewError}</div> : null}
            <button className="primary-btn" type="submit" disabled={submitting}>
              {submitting ? "Enviando..." : "Publicar reseña"}
            </button>
          </form>
        ) : (
          <p className="review-login-hint">Iniciá sesión para dejar tu reseña.</p>
        )}

        {reviewsLoading ? (
          <div className="empty-state">Cargando reseñas...</div>
        ) : reviews.length === 0 ? (
          <div className="empty-state">Todavía no hay reseñas para este producto.</div>
        ) : (
          <div className="reviews-list">
            {reviews.map((r) => (
              <div key={r.id} className="review-card">
                <div className="review-card-header">
                  <strong>{r.authorName}</strong>
                  <Stars value={r.rating} />
                </div>
                <p>{r.comment}</p>
                <div className="review-card-footer">
                  <span className="review-date">{new Date(r.createdAt).toLocaleDateString("es-AR")}</span>
                  {isAdmin ? (
                    <button className="text-link" onClick={() => handleDeleteReview(r.id)}>
                      Eliminar
                    </button>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
