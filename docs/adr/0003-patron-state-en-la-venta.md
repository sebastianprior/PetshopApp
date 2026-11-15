# ADR 0003: Patrón State en el ciclo de vida de la venta

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Patrón de diseño (comportamiento)

## Contexto
La venta (`Order`) es la entidad central: nace en `PAGO_PENDIENTE`, pasa a `COMPLETADA` cuando se aprueba el pago, a `ENVIADA`
cuando se despacha y puede terminar en `CANCELADA`. Las reglas de qué cambio es válido estaban repartidas en `if` sobre
`String` dentro de `VentaService`, y la ruta vieja del panel de administrador (`PUT /api/orders/{id}/status`) no validaba
nada: se podía reactivar una venta cancelada o enviar una que nunca se pagó, sin liberar stock ni reembolsar.

## Decisión
Aplicar el patrón **State**. `EstadoVenta` es un `enum` cuyas constantes implementan el comportamiento de cada estado: cada una
define las acciones que admite (`confirmar`, `enviar`, `cancelar`) y devuelve el estado siguiente; lo que no define lo rechaza
con `TransicionInvalidaException`, que la API traduce a **409 Conflict** con Problem Details.

```
PAGO_PENDIENTE --confirmar--> COMPLETADA --enviar--> ENVIADA   (terminal)
      |                           |
      +--------- cancelar --------+------------------> CANCELADA (terminal)
```

Todo cambio de estado de una venta pasa por esta máquina: el checkout, el worker de pagos pendientes, el aviso de MercadoPago,
la cancelación del cliente, `PATCH /api/v1/ventas/{id}` y el `PUT /api/orders/{id}/status` del panel.

## Alternativas descartadas
- **`if`/`switch` sobre el texto del estado (lo que había):** las reglas se duplican y una ruta se las salta (pasó).
- **Biblioteca de máquinas de estado (Spring Statemachine):** dependencia pesada para 4 estados y 4 transiciones.
- **Una clase por estado con interfaz `EstadoVenta`:** equivalente, pero con 4 estados el `enum` con cuerpos por constante es
  más corto y el compilador garantiza que no hay estados que falten.

## Consecuencias
- (+) Una sola tabla de transiciones, probada completa (4 estados × 3 acciones = 12 combinaciones: 4 permitidas y 8 rechazadas).
- (+) Agregar un estado (por ejemplo `EN_PREPARACION`) toca solo `EstadoVenta`.
- (+) El panel de administrador ya no ofrece transiciones imposibles y la API responde 409 si se fuerzan.
- (−) El estado se sigue guardando como texto en la base (sin migración); `EstadoVenta.de(valor)` lo convierte y un valor
  desconocido no admite ninguna acción.
- (−) El estado heredado `PENDIENTE` (de la API vieja) ya no es válido: no existe en la máquina.

## Implementación y evidencia
- `model/EstadoVenta.java` y `model/TransicionInvalidaException.java`.
- `VentaService.cambiarEstado`, `cancelarPorSolicitud`, `confirmar` y `cancelar` usan `estadoActual(order, acción)`.
- `OrderController.updateStatus` delega en `VentaService.cambiarEstado` (antes asignaba cualquier estado).
- Pruebas: `EstadoVentaTest` (tabla completa de transiciones), `VentaServiceTest` (transiciones parametrizadas y cancelación
  de una venta enviada = 409), `OrderControllerTest` (409 con Problem Details), Newman («Enviar un pedido que ya no admite el
  envío: 409»).
- En vivo: `PATCH /api/v1/ventas/{id}` con `{"estado":"ENVIADA"}` sobre una venta cancelada responde `409`
  `urn:petshop:problem:transicion-invalida`.
