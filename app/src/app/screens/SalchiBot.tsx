import { useEffect, useRef, useState } from "react";
import type { Product } from "../types";
import { askSalchiBot } from "../api";
import { PLACEHOLDER_IMAGE, handleImageError } from "../productImage";
import { SalchiAvatar } from "./SalchiAvatar";

type Props = {
  onOpenProduct: (id: string) => void;
  onAddToCart: (product: Product) => void;
};

type ChatMessage = {
  id: number;
  from: "bot" | "user";
  text: string;
  products?: Product[];
  suggestions?: string[];
};

const GREETING: ChatMessage = {
  id: 0,
  from: "bot",
  text: "¡Guau! Soy Salchi Bot 🌭🎧 Contame qué buscás o cómo es tu mascota y te recomiendo productos de la tienda.",
  suggestions: ["Mi perro se aburre", "Algo para el paseo", "¿Qué ofertas hay?", "Preguntas frecuentes"],
};

const MAX_LENGTH = 300;
const TYPING_MS = 700;

const formatMoney = (value: number) =>
  new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);

export function SalchiBot({ onOpenProduct, onAddToCart }: Props) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([GREETING]);
  const [input, setInput] = useState("");
  const [typing, setTyping] = useState(false);
  const nextId = useRef(1);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages, typing, open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const send = async (raw: string) => {
    const text = raw.trim().slice(0, MAX_LENGTH);
    if (!text || typing) return;

    const history = messages.filter((m) => m.from === "user").map((m) => m.text);
    setMessages((current) => [...current, { id: nextId.current++, from: "user", text }]);
    setInput("");
    setTyping(true);

    const startedAt = Date.now();
    let answer: ChatMessage;
    try {
      const result = await askSalchiBot(text, history);
      answer = { id: nextId.current++, from: "bot", text: result.reply, products: result.products, suggestions: result.suggestions };
    } catch {
      answer = {
        id: nextId.current++,
        from: "bot",
        text: "Uy, se me cortó la comunicación 🎧 Probá de nuevo en un ratito.",
      };
    }
    // una pausa corta para que se sienta como una charla
    const wait = Math.max(0, TYPING_MS - (Date.now() - startedAt));
    window.setTimeout(() => {
      setMessages((current) => [...current, answer]);
      setTyping(false);
    }, wait);
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    void send(input);
  };

  return (
    <>
      {!open ? (
        <button className="salchi-fab" onClick={() => setOpen(true)} aria-label="Abrir chat con Salchi Bot">
          <SalchiAvatar className="salchi-fab-avatar" />
          <span className="salchi-fab-label">¿Te ayudo?</span>
        </button>
      ) : null}

      {open ? (
        <section className="salchi-panel" role="dialog" aria-label="Chat con Salchi Bot">
          <header className="salchi-header">
            <SalchiAvatar className="salchi-header-avatar" />
            <div className="salchi-title">
              <strong>Salchi Bot</strong>
              <span>Asistente de Petshop</span>
            </div>
            <button className="salchi-close" onClick={() => setOpen(false)} aria-label="Cerrar chat">
              ✕
            </button>
          </header>

          <div className="salchi-messages" ref={listRef} aria-live="polite">
            {messages.map((message, index) => (
              <div key={message.id} className={`salchi-row ${message.from}`}>
                <div className="salchi-bubble">{message.text}</div>

                {message.products && message.products.length > 0 ? (
                  <div className="salchi-products">
                    {message.products.map((product) => (
                      <article key={product.id} className="salchi-product">
                        <img
                          src={product.imageUrl || PLACEHOLDER_IMAGE}
                          onError={handleImageError}
                          alt={product.name}
                        />
                        <div className="salchi-product-info">
                          <strong>{product.name}</strong>
                          <span className="salchi-price">
                            {formatMoney(product.precioPromocional ?? product.price)}
                            {product.precioPromocional != null || product.oldPrice ? (
                              <small>{formatMoney(product.precioPromocional != null ? product.price : (product.oldPrice as number))}</small>
                            ) : null}
                          </span>
                          <div className="salchi-product-actions">
                            <button
                              className="secondary-btn"
                              onClick={() => {
                                setOpen(false);
                                onOpenProduct(product.id);
                              }}
                            >
                              Ver
                            </button>
                            <button className="primary-btn" onClick={() => onAddToCart(product)}>
                              {product.hasVariants ? "Elegir" : "Agregar"}
                            </button>
                          </div>
                        </div>
                      </article>
                    ))}
                  </div>
                ) : null}

                {message.from === "bot" && index === messages.length - 1 && !typing && message.suggestions && message.suggestions.length > 0 ? (
                  <div className="salchi-chips">
                    {message.suggestions.map((suggestion) => (
                      <button key={suggestion} className="salchi-chip" onClick={() => void send(suggestion)}>
                        {suggestion}
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            ))}

            {typing ? (
              <div className="salchi-row bot">
                <div className="salchi-bubble salchi-typing" aria-label="Salchi Bot está escribiendo">
                  <span />
                  <span />
                  <span />
                </div>
              </div>
            ) : null}
          </div>

          <form className="salchi-form" onSubmit={handleSubmit}>
            <input
              ref={inputRef}
              value={input}
              maxLength={MAX_LENGTH}
              onChange={(event) => setInput(event.target.value)}
              placeholder="Escribí lo que buscás…"
              aria-label="Mensaje para Salchi Bot"
            />
            <button className="primary-btn" type="submit" disabled={typing || !input.trim()} aria-label="Enviar mensaje">
              Enviar
            </button>
          </form>
        </section>
      ) : null}
    </>
  );
}
