# Matriz de trazabilidad

Une cada historia de usuario (HU) con su requisito, sus casos de prueba ([especificación en Gherkin](historias-y-casos-gherkin.md)) y las pruebas automatizadas que los ejecutan. El enlace se hizo a nivel de **clase o archivo de prueba**: dentro de cada uno están los métodos concretos de cada caso.

> **IDs provisorios.** Las HU y los requisitos (`RF-xx`) se derivaron de la aplicación; si Jira usa otra numeración, se reemplaza en esta tabla y en la especificación. La columna «Jira» se completa cuando el equipo cargue los casos.

**Referencias.** `B:` backend (`app/src/test/java/com/petshop/app/...`), `F:` frontend Vitest (`app/src/app/test/...`), `E2E-nn` Playwright (`app/src/app/e2e/`), `API:` colección Newman y sonda (`app/tests-api/`), `Man.` prueba manual (se ejecuta en la demo o con la hoja de pruebas manuales).

| HU | Requisito | Casos | Pruebas automatizadas | Jira |
| --- | --- | --- | --- | --- |
| HU-01 Explorar el catálogo | RF-01 Listar, filtrar por categoría y buscar | CP-01-1 a CP-01-4 | E2E-01, E2E-01b; F: `ProductsScreen.test`; B: `ProductControllerTest`, `CategoryControllerTest` | |
| HU-02 Detalle de producto y reseñas | RF-02 Variantes, stock y reseñas (1 a 5, una por usuario) | CP-02-1 a CP-02-5 | F: `ProductDetailScreen.test`, `ProductCard.test`; B: `ReviewControllerTest` (CP-02-2, CP-02-4); CP-02-1, CP-02-3 y CP-02-5: Man. | |
| HU-03 Carrito | RF-03 Agregar, cambiar cantidad, quitar; stock | CP-03-1 a CP-03-4 | E2E-13; F: `CartScreen.test`; B: `CartPricingTest`, `VentaServiceTest` (stock insuficiente 409) | |
| HU-04 Envío por código postal | RF-04 Cotizar por zona y código postal | CP-04-1 a CP-04-4 | E2E-02, E2E-04; F: `CartScreen.test`, `ShippingZones.test`; B: `ShippingControllerTest`, `ShippingAndFaqSeedersTest` | |
| HU-05 Cupones | RF-05 Validar y aplicar descuentos | CP-05-1 a CP-05-4 | F: `CartScreen.test`, `checkoutValidation.test`; B: `CouponControllerTest`, `DatosEnvioValidatorTest`, `VentaServiceTest` | |
| HU-06 Envío gratis y regalo | RF-06 Promociones configurables | CP-06-1 a CP-06-4 | F: `CartPromos.test`; B: `CartPromosControllerTest`, `PricingTest`, `VentaServiceTest` | |
| HU-07 Ruleta del día | RF-07 Descuento diario por categoría | CP-07-1 a CP-07-4 | F: `DailyRoulette.test`; B: `RouletteServiceTest`, `RuletaControllerTest` | |
| HU-08 Datos de envío validados | RF-08 Validar nombre, dirección, ciudad, teléfono y cupón (formulario y API) | CP-08-1 a CP-08-5 | F: `checkoutValidation.test` (55), `CartScreen.test`; B: `DatosEnvioValidatorTest` (27), `VentaServiceTest` (422 `datos-envio-invalidos`); API: sonda (filtro de HTML) | |
| HU-09 Pago con tarjeta | RF-09 Autorizar, rechazar, degradar a «pago pendiente», idempotencia, sin sobreventa | CP-09-1 a CP-09-5 | E2E-02, E2E-03, E2E-05; B: `VentaServiceTest`, `VentaControllerTest`, `PasarelaBancariaSimuladaTest`, `PagosSoapTest`, `RepositoriosMySqlTest`; API: `concurrencia_stock.py`, Newman | |
| HU-10 Pago con MercadoPago | RF-10 Preferencia, retorno y aviso (webhook) | CP-10-1 a CP-10-4 | F: `MercadoPago.test`; B: `HttpMercadoPagoClientTest`, `PaymentsControllerTest`, `VentaServiceTest`; CP-10-1 y CP-10-2 también en la demo manual con las cuentas de prueba | |
| HU-11 Registro e ingreso | RF-11 Autenticación con JWT, límite de intentos y renovación | CP-11-1 a CP-11-5 | E2E-06, E2E-07; F: `LoginScreen.test`; B: `AuthControllerTest`, `JwtUtilTest`, `JwtSecurityTest`, `LoginRateLimiterTest`, `RefreshTokenServiceTest` | |
| HU-12 Ingreso con Google | RF-12 Verificar el token de Google | CP-12-1 a CP-12-3 | F: `GoogleSignIn.test`; B: `GoogleLoginTest`, `GoogleTokenVerifierTest`, `GoogleJwksKeySourceTest` | |
| HU-13 Mis pedidos | RF-13 Listar solo los propios, sin datos internos | CP-13-1 a CP-13-3 | E2E-05; F: `OrderConfirmationScreen.test`; B: `OrderControllerTest`, `SensitiveDataTest`; API: BOLA en la sonda y Newman | |
| HU-14 Solicitar devolución | RF-14 Validar compra y unidades | CP-14-1 a CP-14-4 | B: `ReturnControllerTest`; API: Newman | |
| HU-15 Favoritos | RF-15 Guardar y quitar favoritos con sesión | CP-15-1 a CP-15-3 | F: `WishlistScreen.test`; B: `WishlistControllerTest` | |
| HU-16 Preguntas frecuentes y Salchi Bot | RF-16 Responder y recomendar por reglas | CP-16-1 a CP-16-5 | E2E-11, E2E-15; F: `Faq.test`, `SalchiBot.test`; B: `ChatAdvisorTest`, `ChatControllerTest`, `FaqMatcherTest`, `FaqControllerTest` | |
| HU-17 Mi cuenta | RF-17 Cambiar nombre y contraseña | CP-17-1 a CP-17-3 | F: `MyAccountScreen.test`; B: `UserControllerTest`, `AuthControllerTest`; CP-17-3: filtro de HTML en la sonda | |
| HU-18 Acceso al panel | RF-18 Solo `ADMIN`; 401 sin sesión, 403 con rol insuficiente | CP-18-1 a CP-18-4 | E2E-08, E2E-09, E2E-10; B: `AdminGuardTest`, `AdminGuardDeniedTest`, `AdminOnlyInterceptorTest`; API: sonda (28 operaciones de administrador) | |
| HU-19 Productos, categorías e imágenes | RF-19 CRUD con validación, subida acotada y ETag | CP-19-1 a CP-19-4 | B: `ProductControllerTest`, `CategoryControllerTest`, `UploadControllerTest`, `ETagsTest` | |
| HU-20 Pedidos y estados | RF-20 Máquina de estados (State) y 409 | CP-20-1 a CP-20-4 | B: `EstadoVentaTest` (24: 4 válidas y 8 inválidas por combinación), `VentaServiceTest`, `OrderControllerTest`; API: demo del 409 (`demo_entrega2.py`); CP-20-4: Man. | |
| HU-21 Configurar la tienda | RF-21 Cupones, anuncios, promos, zonas y FAQ | CP-21-1 a CP-21-4 | F: `AnnouncementSettings.test`, `ShippingZones.test`, `CartPromos.test`; B: `CouponControllerTest`, `SiteSettingsControllerTest`, `ShippingControllerTest`, `FaqControllerTest` | |
| HU-22 Devoluciones y reembolsos | RF-22 Aprobar, rechazar y reembolsar | CP-22-1 a CP-22-4 | B: `RefundControllerTest`, `ReturnControllerTest` | |
| HU-23 Usuarios | RF-23 Roles y estado, con protecciones | CP-23-1 a CP-23-5 | B: `UserControllerTest` | |
| HU-24 Mensajes fallidos | RF-24 Outbox, consumidor idempotente y DLQ | CP-24-1 a CP-24-4 | B: `BrokerResilienceTest`, `VentaEventsJmsTest`, `BrokerSecurityTest` | |

## Cobertura
- **24 historias, 98 casos.** Todas las historias tienen al menos una prueba automatizada.
- **Casos solo manuales (se ejecutan en la demo):** CP-02-1, CP-02-3, CP-02-5, CP-20-4 (el panel muestra el email del comprador) y la visualización de CP-10-1 y CP-10-2 con las cuentas de prueba de MercadoPago.
- Las pruebas no funcionales (seguridad, rendimiento, accesibilidad y contrato) no están ligadas a una HU concreta: ver el [plan de pruebas](plan-de-pruebas.md) y el [informe de ejecución](informe-de-ejecucion.md).

## Cómo comprobar el enlace
1. Abrir el archivo de prueba de la columna «Pruebas automatizadas» y buscar el comportamiento del caso.
2. Ejecutar la prueba: `.\mvnw.cmd test "-Dtest=<Clase>"` (backend) o `npx vitest run <archivo>` (frontend, en `app/src/app`).
3. Un caso sin prueba automatizada se marca como «Man.» y se ejecuta con la hoja de pruebas manuales.

> **Declaración.** El borrador de esta matriz y de los escenarios Gherkin se redactó con ayuda de IA (ver la [declaración](declaracion-de-ia.md)) y se verificó contra las clases de prueba existentes. El equipo debe revisar los enlaces antes de la defensa.
