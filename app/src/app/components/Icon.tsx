import type { ReactElement } from "react";

/**
 * Set de íconos propio (trazo de 2 px, esquinas redondeadas, 24 x 24) para que todo el sitio se vea consistente
 * y no dependa de emojis, que cambian según el sistema. Se tiñen con `currentColor`.
 */
export type IconName =
  | "paw"
  | "cart"
  | "heart"
  | "search"
  | "user"
  | "menu"
  | "close"
  | "truck"
  | "card"
  | "shield"
  | "headset"
  | "tag"
  | "star"
  | "plus"
  | "minus"
  | "check"
  | "trash"
  | "logout"
  | "wheel"
  | "sun"
  | "moon"
  | "contrast"
  | "home"
  | "package"
  | "arrow-right"
  | "dog"
  | "cat"
  | "bowl"
  | "ball"
  | "bone"
  | "drop"
  | "health"
  | "mail"
  | "gift"
  | "sparkles"
  | "settings";

const PATHS: Record<IconName, ReactElement> = {
  paw: (
    <>
      <ellipse cx="12" cy="16.2" rx="4.6" ry="3.6" />
      <ellipse cx="5.3" cy="11.2" rx="1.9" ry="2.5" />
      <ellipse cx="9.2" cy="6.6" rx="1.9" ry="2.6" />
      <ellipse cx="14.8" cy="6.6" rx="1.9" ry="2.6" />
      <ellipse cx="18.7" cy="11.2" rx="1.9" ry="2.5" />
    </>
  ),
  cart: (
    <>
      <circle cx="9" cy="20" r="1.4" />
      <circle cx="18" cy="20" r="1.4" />
      <path d="M2 3h3l2.7 12.4a2 2 0 0 0 2 1.6h8.2a2 2 0 0 0 2-1.5L21.5 8H6" />
    </>
  ),
  heart: <path d="M20.8 5.6a5.2 5.2 0 0 0-7.4 0L12 7l-1.4-1.4a5.2 5.2 0 0 0-7.4 7.4L12 21l8.8-8a5.2 5.2 0 0 0 0-7.4z" />,
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.3-4.3" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1" />
    </>
  ),
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  close: <path d="M6 6l12 12M18 6L6 18" />,
  truck: (
    <>
      <path d="M2 6h11v10H2zM13 9h4.5l3.5 3.5V16h-8" />
      <circle cx="6.5" cy="18" r="2" />
      <circle cx="17.5" cy="18" r="2" />
    </>
  ),
  card: (
    <>
      <rect x="2" y="5" width="20" height="14" rx="3" />
      <path d="M2 10h20M6 15h4" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3l8 3v6c0 4.5-3.3 8-8 9-4.7-1-8-4.5-8-9V6z" />
      <path d="M8.5 12l2.5 2.5 4.5-5" />
    </>
  ),
  headset: (
    <>
      <path d="M4 14v-2a8 8 0 0 1 16 0v2" />
      <rect x="2.5" y="13" width="4" height="6" rx="2" />
      <rect x="17.5" y="13" width="4" height="6" rx="2" />
      <path d="M19.5 19c0 2-2 3-5 3" />
    </>
  ),
  tag: (
    <>
      <path d="M3 12V4h8l9 9a2 2 0 0 1 0 2.8l-5.2 5.2a2 2 0 0 1-2.8 0z" />
      <circle cx="7.5" cy="8.5" r="1.3" />
    </>
  ),
  star: <path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z" />,
  plus: <path d="M12 5v14M5 12h14" />,
  minus: <path d="M5 12h14" />,
  check: <path d="M4 12.5l5 5L20 6.5" />,
  trash: (
    <>
      <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5" />
    </>
  ),
  logout: (
    <>
      <path d="M9 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4" />
      <path d="M16 8l4 4-4 4M20 12H9" />
    </>
  ),
  wheel: (
    <>
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="2" />
      <path d="M12 3v7M12 14v7M3 12h7M14 12h7M5.6 5.6l5 5M13.4 13.4l5 5M18.4 5.6l-5 5M10.6 13.4l-5 5" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  moon: <path d="M20 14.5A8.5 8.5 0 1 1 9.5 4a6.7 6.7 0 0 0 10.5 10.5z" />,
  contrast: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor" />
    </>
  ),
  home: (
    <>
      <path d="M3 11l9-8 9 8" />
      <path d="M5 10v10h14V10M10 20v-6h4v6" />
    </>
  ),
  package: (
    <>
      <path d="M21 8l-9-5-9 5 9 5z" />
      <path d="M3 8v8l9 5 9-5V8M12 13v8" />
    </>
  ),
  "arrow-right": <path d="M5 12h14M13 6l6 6-6 6" />,
  dog: (
    <>
      <path d="M6 9C3.5 9 3 14 5 16M18 9c2.5 0 3 5 1 7" />
      <path d="M7 7c1-2.5 3-3 5-3s4 .5 5 3v6c0 3-2.5 6-5 6s-5-3-5-6z" />
      <circle cx="9.6" cy="11" r=".8" fill="currentColor" />
      <circle cx="14.4" cy="11" r=".8" fill="currentColor" />
      <path d="M10.5 15h3l-1.5 1.8z" fill="currentColor" />
    </>
  ),
  cat: (
    <>
      <path d="M4 4l3.5 4M20 4l-3.5 4" />
      <path d="M4 4v9a8 8 0 0 0 16 0V4l-3.5 3.5h-9z" />
      <circle cx="9.5" cy="12" r=".8" fill="currentColor" />
      <circle cx="14.5" cy="12" r=".8" fill="currentColor" />
      <path d="M11 15.5h2M12 15.5v1.5M9 17.5c1.5 1 4.5 1 6 0" />
    </>
  ),
  bowl: (
    <>
      <path d="M3 11h18a9 8 0 0 1-18 0z" />
      <path d="M7 11c.5-3.5 3-5 5-5s4.5 1.500 5 5M9 20h6" />
    </>
  ),
  ball: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M3.500 8.500c4.500 1.500 6 6.500 5.500 12M20.500 15.500c-4.500-1.500-6-6.500-5.500-12" />
    </>
  ),
  bone: (
    <>
      <path d="M8 16l8-8" />
      <circle cx="6" cy="14.500" r="2.200" />
      <circle cx="9.500" cy="18" r="2.200" />
      <circle cx="14.500" cy="6" r="2.200" />
      <circle cx="18" cy="9.500" r="2.200" />
    </>
  ),
  drop: <path d="M12 3c4 4.500 6.500 7.500 6.500 11a6.500 6.500 0 0 1-13 0C5.500 10.500 8 7.500 12 3z" />,
  health: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v8M8 12h8" />
    </>
  ),
  mail: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="3" />
      <path d="M3.500 7.500L12 13l8.500-5.500" />
    </>
  ),
  gift: (
    <>
      <rect x="3" y="9" width="18" height="12" rx="2" />
      <path d="M3 13h18M12 9v12M12 9C10 9 7 8.500 7 6.500S10 3.500 12 9c2-5.500 5-4.500 5-2.500S14 9 12 9z" />
    </>
  ),
  sparkles: (
    <>
      <path d="M12 3l1.800 5.200L19 10l-5.200 1.800L12 17l-1.800-5.200L5 10l5.200-1.800z" />
      <path d="M19 16l.8 2.200L22 19l-2.200.8L19 22l-.8-2.200L16 19l2.200-.8z" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2v3M12 19v3M4.200 4.200l2.100 2.100M17.700 17.700l2.100 2.100M2 12h3M19 12h3M4.200 19.800l2.100-2.100M17.700 6.300l2.100-2.100" />
    </>
  ),
};

type Props = {
  name: IconName;
  size?: number;
  className?: string;
  /** Si se indica, el ícono se anuncia a lectores de pantalla; si no, se oculta (es decorativo). */
  title?: string;
  /** Relleno (por ejemplo, corazón de favoritos activo o estrella de puntaje). */
  filled?: boolean;
};

export function Icon({ name, size = 20, className, title, filled = false }: Props) {
  return (
    <svg
      className={className ? `icon ${className}` : "icon"}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? "img" : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
      focusable="false"
    >
      {PATHS[name]}
    </svg>
  );
}

/** Ícono de una categoría según su id (con respaldo por si el admin crea categorías nuevas). */
export function categoryIcon(categoryId: string): IconName {
  const map: Record<string, IconName> = {
    perros: "dog",
    gatos: "cat",
    alimentos: "bowl",
    juguetes: "ball",
    accesorios: "tag",
    higiene: "drop",
    salud: "health",
    reptiles: "sparkles",
  };
  return map[categoryId] ?? "paw";
}
