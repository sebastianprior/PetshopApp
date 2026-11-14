/** El perro salchicha; con `headset` lleva auriculares y micrófono de call center (Salchi Bot). */
export function SalchiAvatar({ className, headset = true }: { className?: string; headset?: boolean }) {
  return (
    <svg className={className} viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false">
      {/* orejas caídas */}
      <ellipse cx="29" cy="72" rx="11" ry="27" fill="#6b3b19" transform="rotate(8 29 72)" />
      <ellipse cx="91" cy="72" rx="11" ry="27" fill="#6b3b19" transform="rotate(-8 91 72)" />
      {/* cabeza y hocico */}
      <ellipse cx="60" cy="62" rx="29" ry="34" fill="#9a5b2e" />
      <ellipse cx="60" cy="86" rx="16" ry="20" fill="#c98a4b" />
      <ellipse cx="60" cy="77" rx="7" ry="5.2" fill="#2b1a0e" />
      <path d="M53 92 Q60 99 67 92" stroke="#2b1a0e" strokeWidth="2.6" fill="none" strokeLinecap="round" />
      {/* ojos */}
      <circle cx="48" cy="58" r="4.6" fill="#1f1209" />
      <circle cx="72" cy="58" r="4.6" fill="#1f1209" />
      <circle cx="49.6" cy="56.4" r="1.5" fill="#ffffff" />
      <circle cx="73.6" cy="56.4" r="1.5" fill="#ffffff" />
      {headset ? (
        <>
          {/* diadema y auriculares */}
          <path d="M27 62 C25 18 95 18 93 62" stroke="#37474f" strokeWidth="6" fill="none" strokeLinecap="round" />
          <rect x="18" y="52" width="16" height="28" rx="8" fill="#37474f" />
          <rect x="86" y="52" width="16" height="28" rx="8" fill="#37474f" />
          <rect x="22" y="57" width="8" height="18" rx="4" fill="#90a4ae" />
          <rect x="90" y="57" width="8" height="18" rx="4" fill="#90a4ae" />
          {/* micrófono */}
          <path d="M94 78 C94 100 84 104 74 102" stroke="#37474f" strokeWidth="3.4" fill="none" strokeLinecap="round" />
          <ellipse cx="70" cy="102" rx="7.5" ry="5" fill="#263238" />
          <ellipse cx="68.5" cy="101" rx="3" ry="1.8" fill="#78909c" />
        </>
      ) : null}
    </svg>
  );
}

