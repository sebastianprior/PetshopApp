# ADR 0009: ETag e If-Match para el control optimista de concurrencia

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Concurrencia en la API

## Contexto
Dos administradores pueden abrir el mismo producto o la misma venta y guardar uno después del otro: el segundo pisa el cambio del
primero sin saberlo (actualización perdida). Defecto D-12 del registro.

## Decisión
Control optimista con el estándar HTTP (RFC 9110, sección 13.1.1):
- `GET /api/v1/ventas/{id}` y `GET /api/products/{id}` devuelven un header `ETag`, una huella SHA-256 de los campos que pueden
  cambiar (`ETags.of`).
- `PATCH /api/v1/ventas/{id}` y `PUT /api/products/{id}` aceptan `If-Match`. Si la huella ya no coincide, responden
  **412 Precondition Failed** (Problem Details `precondicion-fallida`) y no modifican nada.
- Sin `If-Match` la actualización sigue funcionando (compatibilidad con clientes que no lo envían).

## Alternativas descartadas
- **Columna `@Version` de JPA expuesta en el cuerpo:** acopla el contrato a un detalle de persistencia.
- **Bloqueo pesimista:** mantiene bloqueos mientras una persona decide qué escribir.

## Consecuencias
- (+) Una actualización sobre datos viejos se rechaza en lugar de perder el cambio ajeno.
- (−) El cliente tiene que guardar el `ETag` que leyó y mostrar el conflicto al usuario.

## Implementación y evidencia
- `controller/ETags.java`, `VentaController.cambiarEstado`, `ProductController` (`PUT`).
- Pruebas: la sonda de seguridad (`PUT /products/{id}` con `If-Match` desactualizado → 412; `GET` devuelve `ETag`) y los
  tests de controlador.
