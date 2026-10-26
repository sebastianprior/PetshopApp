import type { ReactNode } from "react";

type Props = {
  children: ReactNode;
  /** Largo del texto, para que la velocidad sea pareja (más texto = más tiempo por vuelta). */
  textLength: number;
};

/** Marquesina: el contenido se desplaza de derecha a izquierda sin cortes. Se repite para que el bucle sea continuo. */
export function Marquee({ children, textLength }: Props) {
  const seconds = Math.max(14, Math.round(textLength * 0.22));
  return (
    <div className="marquee">
      <div className="marquee-track" style={{ animationDuration: `${seconds}s` }}>
        <div className="marquee-group">{children}</div>
        <div className="marquee-group" aria-hidden="true">
          {children}
        </div>
      </div>
    </div>
  );
}
