import { useEffect, useRef, useState } from "react";
import type { RouletteState } from "../types";
import { spinRoulette } from "../api";
import { SalchiAvatar } from "./SalchiAvatar";

type Props = {
  state: RouletteState;
  token: string | null;
  onSpun: (state: RouletteState) => void;
  onClose: () => void;
  onGoToCategory: (categoryId: string) => void;
};

/** Cuánto dura la animación del giro (tiene que coincidir con la transición del CSS). */
export const SPIN_MS = 4600;
const EXTRA_TURNS = 6;
const SIZE = 320;
const RADIUS = 150;

const polar = (angleDeg: number, radius: number) => {
  const rad = (angleDeg * Math.PI) / 180;
  return { x: radius * Math.sin(rad), y: -radius * Math.cos(rad) };
};

/** Ángulo (en grados, desde las 12 en sentido horario) del centro del gajo `index`. */
const centerAngle = (index: number, count: number) => (index + 0.5) * (360 / count);

export function DailyRoulette({ state: initialState, token, onSpun, onClose, onGoToCategory }: Props) {
  // El resultado se guarda acá también: la ruleta se queda mostrando el premio aunque el padre tarde en actualizarse.
  const [state, setState] = useState(initialState);
  const count = state.categories.length;
  const wonIndex = state.categoryId ? state.categories.findIndex((c) => c.id === state.categoryId) : -1;

  // Si ya giró hoy, la ruleta arranca apuntando a su categoría.
  const [rotation, setRotation] = useState(wonIndex >= 0 ? 360 - centerAngle(wonIndex, count) : 0);
  const [phase, setPhase] = useState<"idle" | "spinning" | "done">(state.spun ? "done" : "idle");
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<number | null>(null);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      if (timer.current) window.clearTimeout(timer.current);
    };
  }, [onClose]);

  const spin = async () => {
    if (phase !== "idle") return;
    setError(null);
    setPhase("spinning");
    try {
      const result = await spinRoulette(token);
      const index = result.categories.findIndex((c) => c.id === result.categoryId);
      // El servidor ya decidió; acá solo se anima hasta que ese gajo quede bajo la flecha.
      const base = rotation - (rotation % 360);
      setRotation(base + 360 * EXTRA_TURNS + (360 - centerAngle(index, result.categories.length)));
      timer.current = window.setTimeout(() => {
        setState(result);
        setPhase("done");
        onSpun(result);
      }, SPIN_MS);
    } catch (err) {
      setPhase("idle");
      setError(err instanceof Error ? err.message : "No se pudo girar la ruleta");
    }
  };

  const won = state.categoryId ? state.categories.find((c) => c.id === state.categoryId) : null;

  return (
    <div className="roulette-backdrop" onClick={onClose}>
      <section className="roulette-modal" role="dialog" aria-label="Descuento del día" onClick={(event) => event.stopPropagation()}>
        <button className="roulette-close" onClick={onClose} aria-label="Cerrar">
          ✕
        </button>

        <p className="eyebrow">Descuento del día</p>
        <h2>{phase === "done" && won ? `¡${state.percent}% OFF en ${won.name}!` : `Girá y ganá ${state.percent}% OFF`}</h2>
        <p className="roulette-sub">
          {phase === "done" && won
            ? "Se aplica solo en tu carrito a los productos de esa categoría, hasta la medianoche."
            : "Una vez por día: el salchicha sortea una categoría y te regala el descuento."}
        </p>

        <div className="roulette-stage">
          <div className="roulette-pointer" aria-hidden="true" />
          <svg
            className="roulette-wheel"
            viewBox={`${-SIZE / 2} ${-SIZE / 2} ${SIZE} ${SIZE}`}
            style={{ transform: `rotate(${rotation}deg)`, transitionDuration: phase === "spinning" ? `${SPIN_MS}ms` : "0ms" }}
            aria-hidden="true"
          >
            <circle r={RADIUS + 8} fill="#7a4620" />
            {state.categories.map((category, index) => {
              const a = 360 / count;
              const start = polar(index * a, RADIUS);
              const end = polar((index + 1) * a, RADIUS);
              const centerDeg = centerAngle(index, count);
              const flipped = centerDeg > 180;
              return (
                <g key={category.id}>
                  <path
                    d={`M0 0 L${start.x} ${start.y} A${RADIUS} ${RADIUS} 0 0 1 ${end.x} ${end.y} Z`}
                    fill={category.color}
                    stroke="#fffdfb"
                    strokeWidth="3"
                  />
                  <g transform={`rotate(${flipped ? centerDeg + 90 : centerDeg - 90})`}>
                    <text
                      x={flipped ? -50 : 50}
                      y="5"
                      textAnchor={flipped ? "end" : "start"}
                      fontSize="15"
                      fontWeight="800"
                      fill="#3b2a1a"
                    >
                      {category.name}
                    </text>
                  </g>
                </g>
              );
            })}
          </svg>
          <div className="roulette-hub" aria-hidden="true">
            <SalchiAvatar headset={false} className="roulette-hub-dog" />
          </div>
        </div>

        {error ? <div className="error-box">{error}</div> : null}

        {phase === "done" && won ? (
          <div className="roulette-actions">
            <button className="primary-btn" onClick={() => onGoToCategory(won.id)}>
              Ver {won.name}
            </button>
            <button className="secondary-btn" onClick={onClose}>
              Seguir mirando
            </button>
          </div>
        ) : (
          <button className="primary-btn roulette-spin" onClick={() => void spin()} disabled={phase === "spinning"}>
            {phase === "spinning" ? "Girando…" : "¡Girar!"}
          </button>
        )}

        {phase === "done" && state.alreadySpun ? <p className="roulette-note">Ya giraste hoy: mañana vas a poder volver a jugar.</p> : null}
      </section>
    </div>
  );
}
