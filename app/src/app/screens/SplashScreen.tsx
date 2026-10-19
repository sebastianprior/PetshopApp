import { useEffect, useState } from "react";

type Props = {
  onDone: () => void;
};

/** Cuánto dura el paseo del perro antes de que la pantalla se desvanezca. */
export const SPLASH_WALK_MS = 2600;
const FADE_MS = 450;

/** Pantalla de bienvenida: un perro salchicha cruza la pantalla caminando. Un toque la saltea. */
export function SplashScreen({ onDone }: Props) {
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    const fade = setTimeout(() => setLeaving(true), SPLASH_WALK_MS);
    const done = setTimeout(onDone, SPLASH_WALK_MS + FADE_MS);
    return () => {
      clearTimeout(fade);
      clearTimeout(done);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const skip = () => {
    setLeaving(true);
    setTimeout(onDone, FADE_MS);
  };

  return (
    <div
      className={leaving ? "splash leaving" : "splash"}
      role="status"
      aria-label="Cargando Petshop"
      onClick={skip}
    >
      <div className="splash-brand">
        <span className="splash-brand-mark">🐾</span>
        <span>Petshop</span>
      </div>

      <div className="splash-stage" aria-hidden="true">
        <div className="splash-dog">
          <svg viewBox="0 0 240 110" xmlns="http://www.w3.org/2000/svg">
            <ellipse className="dog-shadow" cx="122" cy="104" rx="92" ry="5" />

            {/* patas de atrás y de adelante: cada par se mueve en contrafase */}
            <g className="dog-leg dog-leg-a">
              <rect x="52" y="66" width="11" height="34" rx="5.5" fill="#7a4620" />
            </g>
            <g className="dog-leg dog-leg-b">
              <rect x="150" y="66" width="11" height="34" rx="5.5" fill="#7a4620" />
            </g>

            <g className="dog-body">
              {/* cola */}
              <path className="dog-tail" d="M44 52 C30 50 24 40 20 28" fill="none" stroke="#9a5b2e" strokeWidth="8" strokeLinecap="round" />
              {/* cuerpo largo */}
              <rect x="38" y="42" width="136" height="36" rx="18" fill="#9a5b2e" />
              <ellipse cx="108" cy="72" rx="52" ry="7" fill="#b87438" opacity="0.7" />
              {/* cabeza y hocico */}
              <ellipse cx="184" cy="46" rx="20" ry="18" fill="#9a5b2e" />
              <rect x="194" y="44" width="34" height="16" rx="8" fill="#b87438" />
              <circle cx="227" cy="49" r="4.5" fill="#2b1a0e" />
              {/* ojo */}
              <circle cx="192" cy="40" r="3" fill="#1f1209" />
              <circle cx="193" cy="39" r="1" fill="#ffffff" />
              {/* oreja caída */}
              <ellipse className="dog-ear" cx="176" cy="54" rx="9" ry="18" fill="#6b3b19" transform="rotate(12 176 54)" />
            </g>

            <g className="dog-leg dog-leg-b">
              <rect x="68" y="66" width="11" height="34" rx="5.5" fill="#8a5028" />
            </g>
            <g className="dog-leg dog-leg-a">
              <rect x="134" y="66" width="11" height="34" rx="5.5" fill="#8a5028" />
            </g>
          </svg>
        </div>
        <div className="splash-ground" />
      </div>

      <p className="splash-hint">Cargando…</p>
    </div>
  );
}
