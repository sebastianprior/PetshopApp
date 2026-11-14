import { useEffect, useState } from "react";
import type { CartPromos, Product } from "../types";
import { fetchProducts, updateCartPromos } from "../api";
import { formatMoney } from "../components/ProductCard";

type Props = {
  authToken: string;
  promos: CartPromos;
  onSaved: (promos: CartPromos) => void;
};

/** Envío gratis a partir de un monto y producto de regalo: se configuran acá y se ven en el carrito de la tienda. */
export function CartPromosSettings({ authToken, promos, onSaved }: Props) {
  const [freeShipping, setFreeShipping] = useState(promos.freeShippingEnabled);
  const [threshold, setThreshold] = useState(
    promos.freeShippingThreshold > 0
      ? String(promos.freeShippingThreshold)
      : "",
  );
  const [gift, setGift] = useState(promos.giftEnabled);
  const [giftMin, setGiftMin] = useState(
    promos.giftMinAmount > 0 ? String(promos.giftMinAmount) : "",
  );
  const [giftProductId, setGiftProductId] = useState(
    promos.giftProductId ?? "",
  );
  const [products, setProducts] = useState<Product[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchProducts()
      .then(setProducts)
      .catch((err) =>
        console.error("No se pudieron cargar los productos", err),
      );
  }, []);

  // El regalo no puede tener talles/colores ni estar agotado
  const giftable = products.filter(
    (product) => product.stock > 0 && !product.hasVariants,
  );
  const selected = products.find((product) => product.id === giftProductId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setSaving(true);
    try {
      const result = await updateCartPromos(authToken, {
        freeShippingEnabled: freeShipping,
        freeShippingThreshold: Number(threshold) || 0,
        giftEnabled: gift,
        giftMinAmount: Number(giftMin) || 0,
        giftProductId: giftProductId || null,
      });
      onSaved(result);
      setSaved(true);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "No se pudo guardar la configuración",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="admin-section">
      <form className="admin-form-grid" onSubmit={handleSubmit}>
        <p className="admin-form-wide admin-help">
          Estas dos promos se muestran en el carrito como barras de progreso
          para que la gente sume productos. El monto se compara con el subtotal
          del carrito (con los precios vigentes, antes de cupones y de la
          ruleta).
        </p>

        <div className="admin-form-wide promo-option">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={freeShipping}
              onChange={(e) => setFreeShipping(e.target.checked)}
            />
            <span>Envío gratis a partir de un monto</span>
          </label>
          {freeShipping ? (
            <div className="promo-option-fields">
              <label>
                <span>Monto mínimo para el envío gratis ($)</span>
                <input
                  type="number"
                  min={1}
                  step={1}
                  value={threshold}
                  onChange={(e) => setThreshold(e.target.value)}
                  placeholder="Ej.: 20000"
                />
              </label>
            </div>
          ) : null}
        </div>

        <div className="admin-form-wide promo-option">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={gift}
              onChange={(e) => setGift(e.target.checked)}
            />
            <span>Producto de regalo a partir de un monto</span>
          </label>
          {gift ? (
            <div className="promo-option-fields">
              <label>
                <span>Monto mínimo para el regalo ($)</span>
                <input
                  type="number"
                  min={1}
                  step={1}
                  value={giftMin}
                  onChange={(e) => setGiftMin(e.target.value)}
                  placeholder="Ej.: 50000"
                />
              </label>
              <label>
                <span>Producto de regalo</span>
                <select
                  value={giftProductId}
                  onChange={(e) => setGiftProductId(e.target.value)}
                >
                  <option value="">Elegir producto...</option>
                  {giftable.map((product) => (
                    <option key={product.id} value={product.id}>
                      {product.name} ({product.stock} en stock)
                    </option>
                  ))}
                </select>
              </label>
              <p className="promo-option-wide admin-help">
                {selected
                  ? `Cada cliente que llegue al monto se lleva 1 unidad de "${selected.name}" (valor ${formatMoney(selected.price)}) sin costo, y se descuenta del stock. `
                  : "Solo se pueden elegir productos con stock y sin talles ni colores. "}
                Si el regalo se queda sin stock, deja de ofrecerse solo.
              </p>
            </div>
          ) : null}
        </div>

        {error ? (
          <div className="error-box admin-form-wide" role="alert">
            {error}
          </div>
        ) : null}
        {saved ? (
          <div className="success-box admin-form-wide">
            Cambios guardados. Ya se ven en el carrito.
          </div>
        ) : null}

        <div className="admin-form-actions admin-form-wide">
          <button className="primary-btn" type="submit" disabled={saving}>
            {saving ? "Guardando..." : "Guardar cambios"}
          </button>
        </div>
      </form>
    </section>
  );
}
