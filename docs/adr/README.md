# Registro de decisiones de arquitectura (ADR)

Cada decisión de diseño que condiciona el sistema tiene su ADR: qué problema había, qué se decidió, qué alternativas se
descartaron y qué consecuencias tiene. Todas están **implementadas en el código**; cada ADR nombra las clases y las pruebas
que lo respaldan, para que se puedan verificar en vivo.

Formato (adaptado de Michael Nygard): contexto, decisión, alternativas descartadas, consecuencias, implementación y evidencia.

| N.º | Decisión | Tipo | Estado |
| --- | --- | --- | --- |
| [0001](0001-arquitectura-en-capas.md) | Arquitectura en capas (controller, service, repository, model) | Arquitectura | Aceptada |
| [0002](0002-api-rest-con-problem-details.md) | API REST versionada con errores en Problem Details (RFC 9457) | API | Aceptada |
| [0003](0003-patron-state-en-la-venta.md) | **Patrón State** en el ciclo de vida de la venta (409 ante transición inválida) | **Patrón** | Aceptada |
| [0004](0004-patron-strategy-en-el-orden-de-productos.md) | **Patrón Strategy** para ordenar productos | **Patrón** | Aceptada |
| [0005](0005-patron-adapter-en-las-pasarelas-de-pago.md) | **Patrón Adapter** (puerto y adaptadores) para SOAP y MercadoPago | **Patrón** | Aceptada |
| [0006](0006-outbox-para-los-eventos-de-venta.md) | **Patrón Transactional Outbox** para los eventos de venta | **Patrón** | Aceptada |
| [0007](0007-consumidor-idempotente-y-dlq.md) | **Consumidor idempotente** y cola de mensajes muertos (DLQ) | **Patrón** | Aceptada |
| [0008](0008-idempotency-key-en-el-checkout.md) | Idempotency-Key en la creación de ventas | Resiliencia | Aceptada |
| [0009](0009-etag-y-if-match.md) | ETag e If-Match para el control optimista de concurrencia | Concurrencia | Aceptada |
| [0010](0010-reserva-atomica-de-stock.md) | Reserva atómica de stock en la base de datos | Concurrencia | Aceptada |
| [0011](0011-jwt-y-control-de-acceso-por-rol.md) | JWT y control de acceso por rol (401 vs 403) | Seguridad | Aceptada |
| [0012](0012-timeout-y-fallback-del-pago.md) | Timeout de 5 s con fallback a «pago pendiente» | Resiliencia | Aceptada |

Para proponer un cambio, se agrega un ADR nuevo que reemplaza al anterior (se marca «Reemplazada por»); no se reescribe la historia.
