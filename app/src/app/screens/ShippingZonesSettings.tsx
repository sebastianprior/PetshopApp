import { useEffect, useState } from "react";
import type { ShippingConfig } from "../types";
import { fetchShippingConfig, updateShippingConfig } from "../api";
import { Icon } from "../components/Icon";

type Props = {
  authToken: string;
};

type ZoneDraft = { name: string; postalFrom: string; postalTo: string; cost: string; eta: string };

const MAX_ZONES = 40;
const EMPTY_ZONE: ZoneDraft = { name: "", postalFrom: "", postalTo: "", cost: "", eta: "" };

const toDraft = (config: ShippingConfig) => ({
  defaultCost: String(config.defaultCost),
  defaultEta: config.defaultEta,
  zones: config.zones.map((zone) => ({
    name: zone.name,
    postalFrom: String(zone.postalFrom),
    postalTo: String(zone.postalTo),
    cost: String(zone.cost),
    eta: zone.eta,
  })),
});

/** Costo de envío por código postal: zonas con su rango, costo y plazo, y lo que se cobra al resto del país. */
export function ShippingZonesSettings({ authToken }: Props) {
  const [defaultCost, setDefaultCost] = useState("");
  const [defaultEta, setDefaultEta] = useState("");
  const [zones, setZones] = useState<ZoneDraft[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = (config: ShippingConfig) => {
    const draft = toDraft(config);
    setDefaultCost(draft.defaultCost);
    setDefaultEta(draft.defaultEta);
    setZones(draft.zones);
  };

  useEffect(() => {
    fetchShippingConfig()
      .then(load)
      .catch((err) => setError(err instanceof Error ? err.message : "No se pudo cargar la configuración de envíos"))
      .finally(() => setLoading(false));
  }, []);

  const change = (index: number, field: keyof ZoneDraft, value: string) => {
    setSaved(false);
    setZones((current) => current.map((zone, i) => (i === index ? { ...zone, [field]: value } : zone)));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setSaving(true);
    try {
      const result = await updateShippingConfig(authToken, {
        defaultCost: Number(defaultCost),
        defaultEta: defaultEta.trim(),
        zones: zones.map((zone) => ({
          name: zone.name.trim(),
          postalFrom: Number(zone.postalFrom),
          postalTo: Number(zone.postalTo),
          cost: Number(zone.cost),
          eta: zone.eta.trim(),
        })),
      });
      load(result);
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "No se pudo guardar la configuración de envíos");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="empty-state">Cargando zonas de envío...</div>;
  }

  return (
    <section className="admin-section">
      <form className="admin-form-grid" onSubmit={handleSubmit}>
        <p className="admin-form-wide admin-help">
          El cliente escribe su código postal en el carrito y ve cuánto cuesta y cuánto tarda el envío. Armá zonas con un rango de códigos
          postales (de 1000 a 9999, sin superponerse). Los códigos que no estén en ninguna zona pagan el costo del resto del país. Si el
          carrito alcanza el monto de envío gratis, siempre se muestra "Gratis".
        </p>

        <div className="admin-form-wide promo-option">
          <strong className="promo-option-title">Resto del país</strong>
          <div className="promo-option-fields">
            <label>
              <span>Costo del envío ($)</span>
              <input type="number" min={0} step={1} value={defaultCost} onChange={(e) => { setSaved(false); setDefaultCost(e.target.value); }} />
            </label>
            <label>
              <span>Plazo estimado</span>
              <input value={defaultEta} maxLength={60} onChange={(e) => { setSaved(false); setDefaultEta(e.target.value); }} placeholder="Ej.: 4 a 7 días hábiles" />
            </label>
          </div>
        </div>

        {zones.length === 0 ? <div className="empty-state admin-form-wide">No hay zonas: todo el país paga el costo del resto del país.</div> : null}

        {zones.map((zone, index) => (
          <div key={index} className="admin-form-wide promo-option zone-edit">
            <div className="faq-edit-head">
              <strong>Zona {index + 1}</strong>
              <button
                type="button"
                className="secondary-btn danger"
                aria-label={`Borrar la zona ${index + 1}`}
                onClick={() => {
                  setSaved(false);
                  setZones((current) => current.filter((_, i) => i !== index));
                }}
              >
                <Icon name="trash" size={16} />
              </button>
            </div>
            <div className="zone-fields">
              <label className="zone-name">
                <span>Nombre de la zona</span>
                <input value={zone.name} maxLength={60} onChange={(e) => change(index, "name", e.target.value)} placeholder="Ej.: CABA" />
              </label>
              <label>
                <span>CP desde</span>
                <input type="number" min={1000} max={9999} value={zone.postalFrom} onChange={(e) => change(index, "postalFrom", e.target.value)} placeholder="1000" />
              </label>
              <label>
                <span>CP hasta</span>
                <input type="number" min={1000} max={9999} value={zone.postalTo} onChange={(e) => change(index, "postalTo", e.target.value)} placeholder="1499" />
              </label>
              <label>
                <span>Costo ($)</span>
                <input type="number" min={0} step={1} value={zone.cost} onChange={(e) => change(index, "cost", e.target.value)} placeholder="1200" />
              </label>
              <label>
                <span>Plazo estimado</span>
                <input value={zone.eta} maxLength={60} onChange={(e) => change(index, "eta", e.target.value)} placeholder="24 a 48 hs" />
              </label>
            </div>
          </div>
        ))}

        <div className="admin-form-wide">
          <button
            type="button"
            className="secondary-btn"
            disabled={zones.length >= MAX_ZONES}
            onClick={() => {
              setSaved(false);
              setZones((current) => [...current, { ...EMPTY_ZONE }]);
            }}
          >
            <Icon name="plus" size={16} /> Agregar zona
          </button>
        </div>

        {error ? <div className="error-box admin-form-wide" role="alert">{error}</div> : null}
        {saved ? <div className="success-box admin-form-wide">Cambios guardados. Ya se calculan en el carrito.</div> : null}

        <div className="admin-form-actions admin-form-wide">
          <button className="primary-btn" type="submit" disabled={saving}>
            {saving ? "Guardando..." : "Guardar zonas de envío"}
          </button>
        </div>
      </form>
    </section>
  );
}
