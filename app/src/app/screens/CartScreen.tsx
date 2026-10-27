import { useEffect, useState } from "react";
import { NO_CART_PROMOS, type CartItem, type CartPromos, type ShippingInfo, type ShippingQuote } from "../types";
import { fetchRouletteCartDiscount, fetchShippingQuote, validateCoupon } from "../api";
import { PLACEHOLDER_IMAGE, handleImageError } from "../productImage";
import { Icon } from "../components/Icon";
import { CartPromoBars, qualifiesForFreeShipping, qualifiesForGift } from "../components/CartPromoBars";

type Props = {
  items: CartItem[];
  authToken: string | null;
  productImages: Record<string, string>;
  /** Envío gratis y regalo configurados por el administrador (sin datos = todo apagado). */
  promos?: CartPromos;
  onRemove: (item: CartItem) => void;
  onCheckout: (shipping: Partial<ShippingInfo>, couponCode?: string | null, medioPago?: string) => void;
  onIncrement: (item: CartItem) => void;
  onDecrement: (item: CartItem) => void;
  /** Lleva al catálogo desde el carrito vacío. */
  onContinueShopping?: () => void;
};

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

const POSTAL_CODE_KEY = "petshop_postal_code";
/** Código postal argentino: 4 números, con o sin letras (C1425ABC). */
const POSTAL_CODE_PATTERN = /^[A-Za-z]?\d{4}([A-Za-z]{3})?$/;

// Se recuerda el último código postal para no pedirlo de nuevo (si el navegador no deja guardar, simplemente no se recuerda)
const rememberedPostalCode = () => {
  try {
    return localStorage.getItem(POSTAL_CODE_KEY) ?? "";
  } catch {
    return "";
  }
};

const EMPTY_SHIPPING: ShippingInfo = {
  nombre: "",
  direccion: "",
  ciudad: "",
  codigoPostal: "",
  telefono: "",
};

export function CartScreen({ items, authToken, productImages, promos = NO_CART_PROMOS, onRemove, onCheckout, onIncrement, onDecrement, onContinueShopping }: Props) {
  const [shipping, setShipping] = useState<ShippingInfo>(() => ({ ...EMPTY_SHIPPING, codigoPostal: rememberedPostalCode() }));
  const [quote, setQuote] = useState<ShippingQuote | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [couponInput, setCouponInput] = useState("");
  const [appliedCoupon, setAppliedCoupon] = useState<{ code: string; discountAmount: number } | null>(null);
  const [couponError, setCouponError] = useState<string | null>(null);
  const [couponLoading, setCouponLoading] = useState(false);
  const [medioPago, setMedioPago] = useState("tok_aprobado");
  const [roulette, setRoulette] = useState<{ amount: number; categoryName: string | null }>({ amount: 0, categoryName: null });

  const subtotal = items.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const freeShipping = items.length > 0 && qualifiesForFreeShipping(promos, subtotal);
  // El envío gratis gana; si no, depende del código postal: mientras no se calcula, no se suma al total
  const shippingCost: number | null = items.length === 0 || freeShipping ? 0 : quote ? quote.cost : null;
  const giftIncluded = items.length > 0 && qualifiesForGift(promos, subtotal);
  const discountAmount = appliedCoupon?.discountAmount ?? 0;
  const rouletteAmount = roulette.amount;
  const total = Math.max(0, subtotal + (shippingCost ?? 0) - discountAmount - rouletteAmount);

  // Calcula el envío apenas el código postal está completo (con una pequeña espera mientras se escribe)
  useEffect(() => {
    const code = shipping.codigoPostal.trim();
    setQuoteError(null);
    if (!POSTAL_CODE_PATTERN.test(code)) {
      setQuote(null);
      setQuoteLoading(false);
      if (code.length >= 4) setQuoteError("Revisá el código postal: son 4 números (por ejemplo, 1425).");
      return;
    }
    let active = true;
    setQuoteLoading(true);
    const timer = setTimeout(() => {
      fetchShippingQuote(code)
        .then((result) => {
          if (!active) return;
          setQuote(result);
          try {
            localStorage.setItem(POSTAL_CODE_KEY, code);
          } catch {
            // sin almacenamiento disponible: no pasa nada
          }
        })
        .catch((error) => {
          if (!active) return;
          setQuote(null);
          setQuoteError(error instanceof Error ? error.message : "No pudimos calcular el envío. Probá de nuevo.");
        })
        .finally(() => active && setQuoteLoading(false));
    }, 350);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [shipping.codigoPostal]);

  // El descuento de la ruleta lo calcula el servidor sobre el carrito real (solo los productos de la categoría sorteada).
  useEffect(() => {
    let active = true;
    if (items.length === 0) {
      setRoulette({ amount: 0, categoryName: null });
      return;
    }
    fetchRouletteCartDiscount(authToken)
      .then((result) => active && setRoulette({ amount: result.amount, categoryName: result.categoryName }))
      .catch(() => active && setRoulette({ amount: 0, categoryName: null }));
    return () => {
      active = false;
    };
  }, [items, authToken]);

  useEffect(() => {
    if (!appliedCoupon) return;
    validateCoupon(appliedCoupon.code, subtotal)
      .then((result) => setAppliedCoupon({ code: result.code, discountAmount: result.discountAmount }))
      .catch(() => {
        setAppliedCoupon(null);
        setCouponError("El cupón dejó de ser válido y se quitó del pedido.");
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subtotal]);

  const handleApplyCoupon = async () => {
    if (!couponInput.trim()) return;
    setCouponError(null);
    setCouponLoading(true);
    try {
      const result = await validateCoupon(couponInput.trim(), subtotal);
      setAppliedCoupon({ code: result.code, discountAmount: result.discountAmount });
      setCouponInput("");
    } catch (error) {
      setCouponError(error instanceof Error ? error.message : "No se pudo aplicar el cupón");
    } finally {
      setCouponLoading(false);
    }
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon(null);
    setCouponError(null);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onCheckout(shipping, appliedCoupon?.code, medioPago);
  };

  return (
    <div className="page-shell cart-shell">
      <div className="section-header">
        <h2>Carrito</h2>
      </div>

      {items.length === 0 ? (
        <div className="empty-state">
          <Icon name="cart" size={40} />
          <strong>Tu carrito está vacío.</strong>
          <span>Sumá algo rico o divertido para tu mascota.</span>
          {onContinueShopping ? (
            <button className="primary-btn" onClick={onContinueShopping}>
              Ver productos
            </button>
          ) : null}
        </div>
      ) : (
        <div className="cart-layout">
          <div className="cart-list">
            <CartPromoBars promos={promos} subtotal={subtotal} />
            {items.map((item) => (
              <div key={`${item.productId}-${item.variantId ?? item.variant}`} className="cart-item-card">
                <div className="cart-thumb">
                  <img
                    src={productImages[item.productId] || PLACEHOLDER_IMAGE}
                    onError={handleImageError}
                    alt={item.name}
                  />
                </div>

                <div className="cart-details">
                  <h3>{item.name}</h3>
                  <p>{item.variant}</p>
                  <div className="cart-meta-row">
                    <strong>{formatMoney(item.price)}</strong>
                    <div className="qty-stepper">
                      <button className="secondary-btn" onClick={() => onDecrement(item)} aria-label="Restar cantidad"><Icon name="minus" size={16} /></button>
                      <span>{item.quantity}</span>
                      <button className="secondary-btn" onClick={() => onIncrement(item)} aria-label="Sumar cantidad"><Icon name="plus" size={16} /></button>
                    </div>
                  </div>
                </div>

                <div className="cart-actions">
                  <button className="secondary-btn danger" onClick={() => onRemove(item)}>
                    <Icon name="trash" size={16} /> Quitar
                  </button>
                </div>
              </div>
            ))}
            {giftIncluded ? (
              <div className="cart-item-card gift-card">
                <div className="cart-thumb">
                  <img src={promos.giftProductImageUrl || PLACEHOLDER_IMAGE} onError={handleImageError} alt={promos.giftProductName ?? "Regalo"} />
                </div>
                <div className="cart-details">
                  <h3>{promos.giftProductName}</h3>
                  <p>
                    <span className="gift-tag">
                      <Icon name="gift" size={14} /> Regalo
                    </span>
                  </p>
                  <div className="cart-meta-row">
                    <strong>Gratis</strong>
                    <span className="gift-qty">1 unidad</span>
                  </div>
                </div>
              </div>
            ) : null}
          </div>

          <aside className="cart-summary">
            <h3>Resumen</h3>

            <div className="coupon-box">
              {appliedCoupon ? (
                <div className="coupon-applied">
                  <span>
                    Cupón <strong>{appliedCoupon.code}</strong> aplicado
                  </span>
                  <button type="button" className="text-link" onClick={handleRemoveCoupon}>
                    Quitar
                  </button>
                </div>
              ) : (
                <div className="coupon-input-row">
                  <input
                    value={couponInput}
                    onChange={(e) => setCouponInput(e.target.value)}
                    placeholder="Código de cupón"
                  />
                  <button type="button" className="secondary-btn" onClick={handleApplyCoupon} disabled={couponLoading}>
                    {couponLoading ? "Aplicando..." : "Aplicar"}
                  </button>
                </div>
              )}
              {couponError ? <div className="error-box">{couponError}</div> : null}
            </div>

            <div className="shipping-calc">
              <label>
                <span>Calculá el costo de tu envío</span>
                <input
                  form="checkout-form"
                  required
                  inputMode="text"
                  autoComplete="postal-code"
                  maxLength={8}
                  pattern="[A-Za-z]?[0-9]{4}([A-Za-z]{3})?"
                  title="Código postal de 4 números (por ejemplo, 1425)"
                  placeholder="Tu código postal"
                  value={shipping.codigoPostal}
                  onChange={(e) => setShipping({ ...shipping, codigoPostal: e.target.value })}
                />
              </label>
              {quoteLoading ? <p className="shipping-note">Calculando envío...</p> : null}
              {quoteError ? (
                <p className="shipping-note error" role="alert">
                  {quoteError}
                </p>
              ) : null}
              {quote && !quoteLoading ? (
                <p className="shipping-note ok">
                  <Icon name="truck" size={16} />
                  <span>
                    {quote.matchedZone ? (
                      <>
                        Envío a <strong>{quote.zoneName}</strong>
                      </>
                    ) : (
                      <>
                        Envío al <strong>resto del país</strong>
                      </>
                    )}
                    : llega en {quote.eta}
                  </span>
                </p>
              ) : null}
              {!quote && !quoteLoading && !quoteError ? (
                <p className="shipping-note">
                  {freeShipping ? "¡Tenés envío gratis! Ingresá tu código postal para ver cuándo llega." : "Ingresá tu código postal para ver el costo y el plazo."}
                </p>
              ) : null}
            </div>

            <div className="summary-row">
              <span>Subtotal</span>
              <strong>{formatMoney(subtotal)}</strong>
            </div>
            {discountAmount > 0 ? (
              <div className="summary-row discount-row">
                <span>Descuento</span>
                <strong>-{formatMoney(discountAmount)}</strong>
              </div>
            ) : null}
            {rouletteAmount > 0 ? (
              <div className="summary-row discount-row">
                <span>Ruleta del día{roulette.categoryName ? ` (${roulette.categoryName})` : ""}</span>
                <strong>-{formatMoney(rouletteAmount)}</strong>
              </div>
            ) : null}
            <div className="summary-row">
              <span>Envío</span>
              <strong className={shippingCost === null ? "pending-value" : undefined}>
                {freeShipping ? "Gratis" : shippingCost === null ? "A calcular" : formatMoney(shippingCost)}
              </strong>
            </div>
            <div className="summary-row total-row">
              <span>{shippingCost === null ? "Total (sin envío)" : "Total"}</span>
              <strong>{formatMoney(total)}</strong>
            </div>

            <form id="checkout-form" onSubmit={handleSubmit} className="shipping-form">
              <h4>Datos de envío</h4>
              <label>
                <span>Nombre y apellido</span>
                <input
                  required
                  value={shipping.nombre}
                  onChange={(e) => setShipping({ ...shipping, nombre: e.target.value })}
                />
              </label>
              <label>
                <span>Dirección</span>
                <input
                  required
                  value={shipping.direccion}
                  onChange={(e) => setShipping({ ...shipping, direccion: e.target.value })}
                />
              </label>
              <label>
                <span>Ciudad</span>
                <input
                  required
                  value={shipping.ciudad}
                  onChange={(e) => setShipping({ ...shipping, ciudad: e.target.value })}
                />
              </label>
              <label>
                <span>Teléfono (opcional)</span>
                <input
                  value={shipping.telefono}
                  onChange={(e) => setShipping({ ...shipping, telefono: e.target.value })}
                />
              </label>
              <label>
                <span>Medio de pago (simulado)</span>
                <select value={medioPago} onChange={(e) => setMedioPago(e.target.value)}>
                  <option value="tok_aprobado">Tarjeta de prueba: aprobada</option>
                  <option value="tok_rechazado">Tarjeta de prueba: rechazada</option>
                  <option value="tok_invalido">Tarjeta de prueba: datos inválidos</option>
                  <option value="tok_demora">Pasarela lenta: queda pendiente y se confirma</option>
                  <option value="tok_caido">Pasarela caída: queda pendiente y se cancela</option>
                </select>
              </label>
              <button className="primary-btn block" type="submit">Finalizar compra</button>
              <p className="secure-note">
                <Icon name="shield" size={16} /> Compra protegida: el pago se procesa de forma segura.
              </p>
            </form>
          </aside>
        </div>
      )}
    </div>
  );
}
