import { useMemo, useState } from "react";
import type { Faq } from "../types";
import { Icon } from "../components/Icon";

type Props = {
  items: Faq[];
  loading?: boolean;
  onContact: () => void;
};

/** Pasa a minúsculas y quita tildes para que buscar "envio" encuentre "envío". */
const plain = (text: string) => text.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();

export function FaqScreen({ items, loading = false, onContact }: Props) {
  const [query, setQuery] = useState("");
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  const visible = useMemo(() => {
    const words = plain(query).split(/\s+/).filter(Boolean);
    if (words.length === 0) return items;
    return items.filter((item) => {
      const haystack = plain(`${item.question} ${item.answer}`);
      // Se compara la raíz (primeras 5 letras) para que "devolver" encuentre "devolución" y "envío" encuentre "envíos"
      return words.every((word) => haystack.includes(word.length > 5 ? word.slice(0, 5) : word));
    });
  }, [items, query]);

  return (
    <div className="page-shell faq-shell">
      <header className="catalog-header">
        <span className="eyebrow">
          <Icon name="headset" size={14} /> Ayuda
        </span>
        <h1>Preguntas frecuentes</h1>
        <p>Envíos, pagos, devoluciones y más. Si no encontrás tu respuesta, escribinos o preguntale a Salchi Bot.</p>
      </header>

      <label className="faq-search">
        <span className="sr-only">Buscar en las preguntas frecuentes</span>
        <span className="input-with-icon">
          <Icon name="search" size={18} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscá tu duda: envío, devolución, cuotas..." />
        </span>
      </label>

      {loading ? (
        <div className="empty-state">Cargando preguntas...</div>
      ) : items.length === 0 ? (
        <div className="empty-state">
          <Icon name="headset" size={36} />
          <strong>Todavía no hay preguntas frecuentes.</strong>
        </div>
      ) : visible.length === 0 ? (
        <div className="empty-state">
          <Icon name="search" size={36} />
          <strong>No encontramos preguntas con esa búsqueda.</strong>
          <button className="secondary-btn" onClick={() => setQuery("")}>
            Ver todas
          </button>
        </div>
      ) : (
        <div className="faq-list">
          {visible.map((item, index) => {
            const open = openIndex === index || (query.trim() !== "" && visible.length === 1);
            return (
              <div key={item.id ?? item.question} className={open ? "faq-item open" : "faq-item"}>
                <h2>
                  <button
                    type="button"
                    className="faq-question"
                    aria-expanded={open}
                    aria-controls={`faq-answer-${index}`}
                    onClick={() => setOpenIndex(open ? null : index)}
                  >
                    <span>{item.question}</span>
                    <Icon name={open ? "minus" : "plus"} size={20} />
                  </button>
                </h2>
                {open ? (
                  <div id={`faq-answer-${index}`} className="faq-answer" role="region">
                    {item.answer}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      )}

      <section className="faq-help">
        <div>
          <strong>¿No encontraste lo que buscabas?</strong>
          <span>Nuestro equipo te responde de lunes a viernes de 9 a 18 hs.</span>
        </div>
        <button className="primary-btn" onClick={onContact}>
          Contactanos
        </button>
      </section>
    </div>
  );
}
