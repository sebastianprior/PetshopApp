# ADR 0006: Patrón Transactional Outbox para los eventos de venta

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Patrón de integración / mensajería

## Contexto
Cada venta confirmada, cancelada o con pago fallido genera un evento para la mensajería (`ventas.confirmadas`,
`ventas.canceladas`, `pagos.fallidos`). La primera versión publicaba directo al broker al terminar la operación: si el broker
estaba caído solo se registraba el error y el evento **se perdía** (defecto D-03 del registro de defectos). La condición de
resiliencia pide que, si se apaga el broker, no se pierdan mensajes.

## Decisión
Aplicar el patrón **Outbox**. Apenas `VentaService` guarda el cambio de la venta, publica un evento de dominio y
`VentaEventPublisher` lo guarda como una fila en la tabla `outbox_events`, en el mismo hilo y antes de responder al cliente.
`OutboxRelay`, un proceso programado (cada 2 s), lee las filas pendientes en orden y las entrega al broker;
solo marca una fila como entregada cuando el broker la aceptó, y cuenta los intentos fallidos.

El broker nunca está en el camino de la venta: el evento se guarda en la base y recién después se entrega, así que una caída
o un reinicio del broker no lo pierde. Los guardados de la venta y del evento son dos escrituras consecutivas, **no una única
transacción de base de datos** (`VentaService` no usa `@Transactional`); ver la consecuencia correspondiente.

## Alternativas descartadas
- **Publicar directo al broker tras el commit:** deja una ventana en la que la venta existe y el evento no.
- **Transacción distribuida (XA) entre la base y el broker:** compleja y frágil para este alcance.
- **Reintentos en memoria:** se pierden si se reinicia el backend.

## Consecuencias
- (+) Con el broker apagado, los eventos quedan en la base y salen cuando vuelve: no se pierde ninguno.
- (+) Mantiene el orden de los eventos de una venta.
- (−) Entrega **al menos una vez**: pueden llegar duplicados. Por eso el consumidor es idempotente (ADR 0007).
- (−) Una tabla más y un proceso periódico.
- (−) Como la venta y el evento no se guardan en una sola transacción, si el proceso se cae justo entre los dos guardados la
  venta queda registrada y el evento no se genera (una notificación que no sale; la venta y el cobro no se ven afectados).
  Mejora prevista: envolver ambos guardados en una transacción para que sean atómicos.

## Implementación y evidencia
- `model/OutboxEvent.java`, `repository/OutboxEventRepository.java`, `service/OutboxRelay.java`,
  `service/VentaEventPublisher.java`.
- El broker Artemis embebido persiste en disco con `ARTEMIS_PERSISTENT=true` (`Dockerfile` y `docker-compose.yml`).
- Pruebas: `BrokerResilienceTest` (broker caído y recuperado, sin pérdida y en orden), `VentaEventsJmsTest`, y
  `RepositoriosMySqlTest` contra MySQL real.
