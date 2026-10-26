import type { CartPromos } from "../types";
import { Icon } from "./Icon";
import { formatMoney } from "./ProductCard";

type Props = {
  promos: CartPromos;
  subtotal: number;
};

/** ¿Este carrito consigue el envío gratis? (mismo criterio que el servidor: subtotal >= monto configurado) */
export function qualifiesForFreeShipping(promos: CartPromos, subtotal: number): boolean {
  return promos.freeShippingEnabled && promos.freeShippingThreshold > 0 && subtotal >= promos.freeShippingThreshold;
}

/** ¿Este carrito se lleva el regalo? (activo, disponible y con el monto alcanzado) */
export function qualifiesForGift(promos: CartPromos, subtotal: number): boolean {
  return promos.giftEnabled && promos.giftAvailable && promos.giftMinAmount > 0 && subtotal >= promos.giftMinAmount;
}

function ProgressBar({ icon, subtotal, goal, reached, missingText, reachedText }: {
  icon: "truck" | "gift";
  subtotal: number;
  goal: number;
  reached: boolean;
  missingText: string;
  reachedText: string;
}) {
  const percent = Math.min(100, Math.round((subtotal / goal) * 100));
  return (
    <div className={reached ? "promo-bar reached" : "promo-bar"}>
      <div className="promo-bar-text">
        <span className="promo-bar-icon">
          <Icon name={reached ? "check" : icon} size={18} />
        </span>
        <span>{reached ? reachedText : missingText}</span>
      </div>
      <div className="promo-bar-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} aria-label={reached ? reachedText : missingText}>
        <div className="promo-bar-fill" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}

/** Barras de progreso del carrito: cuánto falta para el envío gratis y para el regalo que configuró el administrador. */
export function CartPromoBars({ promos, subtotal }: Props) {
  const showShipping = promos.freeShippingEnabled && promos.freeShippingThreshold > 0;
  const showGift = promos.giftEnabled && promos.giftAvailable && promos.giftMinAmount > 0;
  if (!showShipping && !showGift) return null;

  return (
    <div className="promo-bars">
      {showShipping ? (
        <ProgressBar
          icon="truck"
          subtotal={subtotal}
          goal={promos.freeShippingThreshold}
          reached={qualifiesForFreeShipping(promos, subtotal)}
          missingText={`Sumá ${formatMoney(promos.freeShippingThreshold - subtotal)} más y el envío es gratis`}
          reachedText="¡Tenés envío gratis!"
        />
      ) : null}
      {showGift ? (
        <ProgressBar
          icon="gift"
          subtotal={subtotal}
          goal={promos.giftMinAmount}
          reached={qualifiesForGift(promos, subtotal)}
          missingText={`Sumá ${formatMoney(promos.giftMinAmount - subtotal)} más y llevate de regalo ${promos.giftProductName ?? "un producto"}`}
          reachedText={`¡Te llevás de regalo ${promos.giftProductName ?? "un producto"}!`}
        />
      ) : null}
    </div>
  );
}
