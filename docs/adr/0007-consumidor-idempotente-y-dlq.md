# ADR 0007: Consumidor idempotente y cola de mensajes muertos (DLQ)

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Patrón de integración / mensajería

## Contexto
Con entrega «al menos una vez» (ADR 0006) un mismo evento puede llegar más de una vez, y un mensaje puede fallar al procesarse
siempre (por ejemplo, un cuerpo que no se puede leer). Procesar dos veces el mismo evento duplicaría efectos (avisos, métricas),
y reintentar para siempre bloquearía la cola. Defecto D-10 del registro.

## Decisión
- **Consumidor idempotente.** Cada evento lleva un `eventId`. `VentaEventListener.procesarUnaVez` guarda el `eventId` en la tabla
  `processed_events` y descarta el mensaje si ya estaba (detección de duplicados).
- **Reintentos y DLQ.** Un mensaje que falla se reintenta; tras **3 entregas** el broker lo mueve a la cola `DLQ`.
- **Reproceso.** El administrador ve la cola con `GET /api/admin/dlq` y la reprocesa con `POST /api/admin/dlq/reprocess`
  (`DlqService`). El acceso a la DLQ está restringido por rol en el broker (`BrokerSecurity`).

## Alternativas descartadas
- **Confiar en que el broker no duplica:** ningún broker lo garantiza con entrega al menos una vez.
- **Reintentar indefinidamente:** un mensaje «venenoso» bloquea todo lo que viene detrás.
- **Descartar el mensaje tras fallar:** se pierde información sin que nadie se entere.

## Consecuencias
- (+) Efecto exactamente una vez a partir de una entrega al menos una vez.
- (+) Los mensajes que fallan quedan visibles y recuperables, sin bloquear la cola.
- (−) La tabla `processed_events` crece (conviene una limpieza periódica en producción).

## Implementación y evidencia
- `service/VentaEventListener.java`, `model/ProcessedEvent.java`, `repository/ProcessedEventRepository.java`,
  `service/DlqService.java`, `controller/DlqController.java`, `config/BrokerSecurity.java`.
- `spring.artemis.embedded.queues=ventas.confirmadas,ventas.canceladas,pagos.fallidos,DLQ`.
- Pruebas: `VentaEventsJmsTest` (duplicado descartado, 3 intentos y paso a DLQ, reproceso), `BrokerResilienceTest`, y la sonda
  de seguridad (`GET /admin/dlq`: 401 sin token, 403 con cliente, 200 con administrador).
