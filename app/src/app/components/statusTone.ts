/** Color del distintivo de estado: verde (bien), amarillo (en espera), rojo (problema) o azul (en camino). */
export function statusTone(status: string): "ok" | "warn" | "bad" | "info" {
  const value = status.toUpperCase();
  if (/CANCEL|RECHAZ|DESHABILIT|INACTIV/.test(value)) return "bad";
  if (/PENDIENTE|ESPERA/.test(value)) return "warn";
  if (/ENVIAD|CAMINO/.test(value)) return "info";
  return "ok";
}
