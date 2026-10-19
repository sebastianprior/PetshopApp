import { PLACEHOLDER_IMAGE, handleImageError } from "../productImage";
import { useEffect, useState } from "react";
import type { Product, ProductVariant, Review } from "../types";
import { deleteReview, fetchProductReviews, fetchProductVariants, submitReview } from "../api";

type Props = {
  product: Product | null;
  authToken: string | null;
  isAdmin: boolean;
  wishlist: string[];
  onToggleWishlist: (productId: string) => void;
  onBack: () => void;
  onAddToCart: (product: Product, variant?: ProductVariant) => void;
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

const variantLabel = (variant: ProductVariant) =>
  [variant.talle ? `Talle ${variant.talle}` : null, variant.color].filter(Boolean).join(" / ");

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
      <button className="secondary-btn" onClick={onBack}>← Volver</button>

      <div className="product-detail">
        <div className="product-detail-image">
          <img
            src={displayImageUrl || PLACEHOLDER_IMAGE}
            onError={handleImageError}
            alt={product.name}
          />
          {product.badge ? <span className="product-badge">{product.badge}</span> : null}
          {!isAdmin ? (
            <button
              className={wishlist.includes(product.id) ? "wishlist-heart active" : "wishlist-heart"}
              onClick={() => onToggleWishlist(product.id)}
              aria-label={wishlist.includes(product.id) ? "Quitar de favoritos" : "Agregar a favoritos"}
            >
              ♥
            </button>
          ) : null}
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
                          onClick={() => setSelectedVariantId(variant.id)}
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
                <button
                  className="primary-btn"
                  onClick={() => onAddToCart(product, selectedVariant)}
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

      <section className="reviews-section">
        <div className="section-header">
          <h2>Reseñas{averageRating != null ? ` — ⭐ ${averageRating.toFixed(1)} (${reviews.length})` : ""}</h2>
        </div>

        {authToken ? (
          <form className="review-form" onSubmit={handleSubmitReview}>
            <label>
              <span>Tu puntaje</span>
              <select value={rating} onChange={(e) => setRating(Number(e.target.value))}>
                <option value={5}>⭐⭐⭐⭐⭐ Excelente</option>
                <option value={4}>⭐⭐⭐⭐ Muy bueno</option>
                <option value={3}>⭐⭐⭐ Bueno</option>
                <option value={2}>⭐⭐ Regular</option>
                <option value={1}>⭐ Malo</option>
              </select>
            </label>
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
                  <span>{"⭐".repeat(r.rating)}</span>
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
