import { useState } from "react";
import type { SiteSettings } from "../types";
import { updateSiteSettings } from "../api";

type Props = {
  authToken: string;
  settings: SiteSettings;
  onSaved: (settings: SiteSettings) => void;
};

const MODE_HELP: Record<SiteSettings["announcementMode"], string> = {
  AUTO: "Muestra automáticamente los productos que tienen precio promocional.",
  CUSTOM: "Muestra el texto que escribas abajo.",
  OFF: "No muestra la barra verde.",
};

export function AnnouncementSettings({ authToken, settings, onSaved }: Props) {
  const [topbar, setTopbar] = useState(settings.topbarItems.join("\n"));
  const [mode, setMode] = useState<SiteSettings["announcementMode"]>(settings.announcementMode);
  const [text, setText] = useState(settings.announcementText);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setSaving(true);
    try {
      const result = await updateSiteSettings(authToken, {
        topbarItems: topbar.split("\n").map((line) => line.trim()).filter(Boolean),
        announcementMode: mode,
        announcementText: text.trim(),
      });
      onSaved(result);
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "No se pudo guardar la configuración");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="admin-section">
      <form className="admin-form-grid" onSubmit={handleSubmit}>
        <label className="admin-form-wide">
          <span>Barra superior (naranja): un texto por línea, hasta 5. Vacía = se oculta</span>
          <textarea
            rows={4}
            value={topbar}
            onChange={(e) => setTopbar(e.target.value)}
            placeholder={"🚚 Envíos a todo el país\n💳 6 cuotas sin interés"}
          />
        </label>

        <label>
          <span>Barra de anuncio (verde)</span>
          <select value={mode} onChange={(e) => setMode(e.target.value as SiteSettings["announcementMode"])}>
            <option value="AUTO">Automática (ofertas)</option>
            <option value="CUSTOM">Texto propio</option>
            <option value="OFF">Oculta</option>
          </select>
        </label>
        <p className="admin-form-wide admin-help">{MODE_HELP[mode]}</p>

        {mode === "CUSTOM" ? (
          <label className="admin-form-wide">
            <span>Texto del anuncio</span>
            <input
              value={text}
              maxLength={200}
              onChange={(e) => setText(e.target.value)}
              placeholder="🔥 Black Friday: 30% OFF en toda la tienda"
            />
          </label>
        ) : null}

        {error ? <div className="error-box admin-form-wide">{error}</div> : null}
        {saved ? <div className="success-box admin-form-wide">Cambios guardados. Ya se ven en la tienda.</div> : null}

        <div className="admin-form-actions admin-form-wide">
          <button className="primary-btn" type="submit" disabled={saving}>
            {saving ? "Guardando..." : "Guardar cambios"}
          </button>
        </div>
      </form>
    </section>
  );
}
