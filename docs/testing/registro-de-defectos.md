# Registro de defectos

Todos los defectos de esta tabla los encontraron las propias pruebas (no la cátedra) y están **cerrados** con una prueba que los cubre. Falta vincular el ID de Jira y la HU de cada uno. Severidad según la sección 9 de la rúbrica.

| ID | Sev. | Defecto y cómo se reprodujo | Causa raíz | Cierre |
| --- | --- | --- | --- | --- |
| D-01 | Crítica | **Sobreventa**: con stock 1 y 12 compradores simultáneos, 10 compras salieron bien (`concurrencia_stock.py`) | El stock se comprobaba en un bucle y se descontaba leyendo y reescribiendo (condición de carrera) | `UPDATE ... WHERE stock >= n` atómico, todo el carrito o nada; prueba con 30 hilos en MySQL real |
| D-02 | Crítica | **Checkout viejo sin pago**: `POST /api/cart/checkout` creaba un pedido `COMPLETADA` sin autorizar ningún pago | Endpoint legado olvidado, sin uso en el frontend | Eliminado |
| D-03 | Crítica | **Eventos perdidos** si el broker estaba caído: el publicador solo registraba el error | Publicación directa al broker, sin persistencia | Outbox en base de datos con reintentos y orden; broker persistente en Docker |
| D-04 | Mayor | Devolución de productos **nunca comprados** o de más unidades que las compradas (Newman) | No se validaba la compra | Se descuentan compradas y ya pedidas (las rechazadas no cuentan); los regalos no se devuelven |
| D-05 | Mayor | Token inválido o vencido en rutas de administrador daba **403 en vez de 401**; con el token vencido la tienda no renovaba la sesión | `AdminGuard` devolvía 403 para todo | `denied()` distingue 401 y 403 en 28 operaciones |
| D-06 | Mayor | Un cuerpo incompleto a una ruta de administrador daba 400 antes del 403 | La validación del cuerpo ocurre antes del control de rol | `@AdminOnly` + interceptor que controla el rol antes de leer el cuerpo |
| D-07 | Mayor | **Faltaban headers de seguridad** (CSP, HSTS, nosniff…) en la API y en la tienda | Nunca se habían configurado | Filtro en la API; nginx y Vercel en la tienda; CSP verificada en navegador |
| D-08 | Mayor | HTML guardado en nombres (`<script>` en el registro) | Sin validación de entrada | Filtro que rechaza etiquetas con 400 (contraseñas excluidas) |
| D-09 | Mayor | Llamada vencida a la pasarela **quedaba colgada** hasta 30 s y los hilos no tenían tope | No se cancelaba el `Future`; pool sin límite | `cancel(true)`, timeout SOAP 5 s, pool acotado con degradación a "pago pendiente" |
| D-10 | Mayor | Mensajes duplicados se procesaban dos veces; los fallidos no iban a una cola de mensajes muertos | Consumidor sin identificador de evento ni política de reintentos | `eventId` + tabla de procesados; 3 intentos y `DLQ` con reproceso de administrador |
| D-11 | Mayor | La API aceptaba productos sin nombre, con precio 0 o negativo, sin categoría o con stock negativo | Sin validación | 400 con la causa (producto y categoría) |
| D-12 | Mayor | Sin control de concurrencia en actualizaciones (sin ETag) | No implementado | `ETag` + `If-Match` con 412 en venta y producto |
| D-13 | Menor | Contrastes de color insuficientes (2,9:1 en el anuncio; 3,9:1 en etiquetas; 4,1–4,45:1 en textos atenuados) | Paleta | Colores nuevos con ≥ 4,5:1; axe y Lighthouse en el CI |
| D-14 | Menor | Prueba de frontend inestable: 1 de 3 corridas en el CI | Revisaba `document.title` antes de que el efecto lo actualizara | Espera con `waitFor` |
| D-15 | Menor | OpenAPI inexacto: 11 endpoints de lista documentados como `object`; campos nulos como `string` | `ResponseEntity<?>` sin esquema | Esquemas declarados; Schemathesis 902/902 |
| D-16 | Menor | Orden de títulos saltado (h1→h4) y producto sin nombre dejaba la imagen sin `alt` | Estructura del pie y de la grilla | Título oculto de nivel 2, pie en h3, `alt` de reemplazo |

## Defectos abiertos
| Sev. | Descripción | Mitigación |
| --- | --- | --- |
| Mayor | El sembrado de desarrollo crea `admin@petshop.com / admin123` y `juan@petshop.com / 123456` | Solo desarrollo (README). Antes de publicar: definir la contraseña del administrador por variable de entorno y quitar al usuario de ejemplo |
| Menor | Los emails son simulados (archivos en `outbox/`) | Documentado; falta SMTP real |
| Menor | `style-src 'unsafe-inline'` en la CSP de la tienda (estilos en línea de React) | ZAP lo marca Medium; aceptado, no ejecuta scripts |
| Menor | Vulnerabilidades solo de desarrollo en Vite y vitest (`npm audit`: 5; en producción 0) | No llegan al paquete publicado; migrar Vite/vitest a una versión mayor |

Máximo admitido: 0 bloqueantes, 0 críticos, 3 mayores con workaround. **Hay 1 mayor abierto.**

## Lecciones aprendidas (3 defectos que llegaron tarde)
1. **D-01, sobreventa.** Llegó tarde porque las pruebas unitarias usaban repositorios simulados que mutaban el objeto en memoria, y un simulacro no tiene carreras. Lo habría atrapado una prueba con compradores simultáneos contra la base real, que ahora existe (`concurrencia_stock.py` y la de 30 hilos en MySQL).
2. **D-02, checkout sin pago.** Sobrevivió porque tenía pruebas que lo daban por bueno y nadie revisó qué endpoints ya no usaba el frontend. Lo habría atrapado un recorrido de todos los endpoints (la colección de Postman) preguntando "¿qué pasa si lo llamo sin pagar?".
3. **D-04, devoluciones sin compra.** El controlador no tenía ninguna prueba del pedido de devolución, solo de la aprobación. Lo habría atrapado un caso negativo por cada regla de negocio de cada HU.
