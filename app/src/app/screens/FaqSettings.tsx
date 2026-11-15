import { useState } from "react";
import type { Faq } from "../types";
import { updateFaq } from "../api";
import { Icon } from "../components/Icon";

type Props = {
  authToken: string;
  items: Faq[];
  onSaved: (items: Faq[]) => void;
};

const MAX_ITEMS = 30;
const MAX_QUESTION = 200;
const MAX_ANSWER = 1000;

/** Editor de las preguntas frecuentes: agregar, editar, reordenar y borrar. Salchi Bot responde con lo que se guarda acá. */
export function FaqSettings({ authToken, items, onSaved }: Props) {
  const [draft, setDraft] = useState<Faq[]>(items.map((item) => ({ ...item })));
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const change = (index: number, field: "question" | "answer", value: string) => {
    setSaved(false);
    setDraft((current) => current.map((item, i) => (i === index ? { ...item, [field]: value } : item)));
  };

  const move = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= draft.length) return;
    setSaved(false);
    setDraft((current) => {
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };

  const remove = (index: number) => {
    setSaved(false);
    setDraft((current) => current.filter((_, i) => i !== index));
  };

  const add = () => {
    setSaved(false);
    setDraft((current) => [...current, { question: "", answer: "" }]);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    const incomplete = draft.findIndex((item) => !item.question.trim() || !item.answer.trim());
    if (incomplete >= 0) {
      setError(`La pregunta ${incomplete + 1} necesita pregunta y respuesta (o borrala).`);
      return;
    }
    setSaving(true);
    try {
      const result = await updateFaq(
        authToken,
        draft.map((item) => ({ question: item.question.trim(), answer: item.answer.trim() })),
      );
      onSaved(result);
      setDraft(result.map((item) => ({ ...item })));
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "No se pudieron guardar las preguntas");
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="admin-section">
      <form className="admin-form-grid" onSubmit={handleSubmit}>
        <p className="admin-form-wide admin-help">
          Estas preguntas se muestran en la página "Preguntas frecuentes" y Salchi Bot las usa para responder a los clientes. Escribí
          la pregunta como la diría un cliente y una respuesta clara; Salchi Bot elige la que mejor encaja con lo que le escriben.
        </p>

        {draft.length === 0 ? <div className="empty-state admin-form-wide">No hay preguntas. Agregá la primera.</div> : null}

        {draft.map((item, index) => (
          <div key={item.id ?? `new-${index}`} className="admin-form-wide faq-edit">
            <div className="faq-edit-head">
              <strong>Pregunta {index + 1}</strong>
              <div className="faq-edit-actions">
                <button type="button" className="secondary-btn" onClick={() => move(index, -1)} disabled={index === 0} aria-label={`Subir la pregunta ${index + 1}`}>
                  <Icon name="arrow-right" size={16} className="icon-up" />
                </button>
                <button
                  type="button"
                  className="secondary-btn"
                  onClick={() => move(index, 1)}
                  disabled={index === draft.length - 1}
                  aria-label={`Bajar la pregunta ${index + 1}`}
                >
                  <Icon name="arrow-right" size={16} className="icon-down" />
                </button>
                <button type="button" className="secondary-btn danger" onClick={() => remove(index)} aria-label={`Borrar la pregunta ${index + 1}`}>
                  <Icon name="trash" size={16} />
                </button>
              </div>
            </div>
            <label>
              <span>Pregunta</span>
              <input value={item.question} maxLength={MAX_QUESTION} onChange={(e) => change(index, "question", e.target.value)} placeholder="¿Cuánto tarda en llegar mi pedido?" />
            </label>
            <label>
              <span>Respuesta</span>
              <textarea rows={3} value={item.answer} maxLength={MAX_ANSWER} onChange={(e) => change(index, "answer", e.target.value)} placeholder="Escribí la respuesta..." />
            </label>
          </div>
        ))}

        <div className="admin-form-wide">
          <button type="button" className="secondary-btn" onClick={add} disabled={draft.length >= MAX_ITEMS}>
            <Icon name="plus" size={16} /> Agregar pregunta
          </button>
          {draft.length >= MAX_ITEMS ? <span className="admin-help"> Máximo {MAX_ITEMS} preguntas.</span> : null}
        </div>

        {error ? <div className="error-box admin-form-wide" role="alert">{error}</div> : null}
        {saved ? <div className="success-box admin-form-wide">Cambios guardados. Ya se ven en la tienda y Salchi Bot los usa.</div> : null}

        <div className="admin-form-actions admin-form-wide">
          <button className="primary-btn" type="submit" disabled={saving}>
            {saving ? "Guardando..." : "Guardar cambios"}
          </button>
        </div>
      </form>
    </section>
  );
}
