# ADR 0008: Idempotency-Key en la creación de ventas

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Resiliencia de la API

## Contexto
Un cliente que no recibe respuesta (red lenta, timeout) reintenta el checkout. Sin protección, un reintento crearía **otra
venta y otro cobro** por el mismo carrito.

## Decisión
`POST /api/v1/ventas` exige el header `Idempotency-Key`. `VentaService.crear` busca la clave en la tabla `idempotency_records`:
- Si ya existe con el mismo contenido, **repite la respuesta original** (mismo código, misma venta) sin volver a cobrar.
- Si existe pero el cuerpo o la persona son distintos, responde **409** `idempotency-key-reutilizada`: la clave no se puede
  usar para otra compra. Sin el header, responde 400.
- Si no existe, procesa la compra y guarda el resultado (`remember`): éxito, rechazo o datos inválidos. Los fallos
  transitorios (por ejemplo, MercadoPago caído) no se guardan para que el cliente pueda reintentar con la misma clave.

## Alternativas descartadas
- **Detectar duplicados por contenido del carrito:** dos compras legítimas iguales seguidas serían tratadas como una.
- **Bloquear el botón en el front:** no protege contra reintentos automáticos ni contra otros clientes de la API.

## Consecuencias
- (+) El reintento es seguro: no hay doble cobro ni doble descuento de stock.
- (−) El cliente tiene que generar y conservar una clave por intento de compra (la tienda la genera por carrito).
- (−) Los registros de idempotencia se acumulan (se pueden purgar pasado un plazo).

## Implementación y evidencia
- `model/IdempotencyRecord.java`, `repository/IdempotencyRecordRepository.java`, `VentaService.crear/replay/remember`.
- Pruebas: `VentaServiceTest` (`sameIdempotencyKeyAndBodyReplaysTheOriginalResult`, `sameIdempotencyKeyWithDifferentBodyReturns409`),
  `VentaControllerTest` (400 sin el header) y casos de la colección Newman sobre `POST /api/v1/ventas`.
