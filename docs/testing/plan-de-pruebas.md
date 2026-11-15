# Plan y estrategia de pruebas

## Alcance
**Dentro:** tienda (React), API REST v1 y legada (Spring Boot), pasarela de pagos SOAP, mensajería (Artemis), MercadoPago (modo prueba), administración, Salchi Bot (reglas, sin modelo de lenguaje).
**Fuera:** MercadoPago en producción, envío real de emails (hoy simulado en `outbox/`), el ingreso con Google más allá de la verificación del token, la infraestructura de Render/Vercel/Aiven.

## Niveles y herramientas
| Nivel | Herramienta | Dónde |
| --- | --- | --- |
| Unitario y de componente (backend) | JUnit 5, Mockito, AssertJ | `app/src/test` |
| Integración con dependencias reales | Artemis real; MySQL 8.4 en contenedor (Testcontainers) | `BrokerResilienceTest`, `RepositoriosMySqlTest` |
| Unitario y de componente (frontend) | Vitest + Testing Library | `app/src/app/test` |
| Punta a punta | Playwright (Chromium) | `app/src/app/e2e` |
| API | Postman + Newman; sonda propia | `app/tests-api` |
| Contrato | Schemathesis contra el OpenAPI; pruebas del WSDL | `seguridad.yml`, `PagosSoapTest` |
| Mutación | PIT sobre `service` y `payment` | `pom.xml` |
| Carga | k6 | `app/tests-api/carga.k6.js` |
| Accesibilidad y responsive | axe (WCAG 2.2 AA), Lighthouse, 360/768/1280 px | E2E y `seguridad.yml` |
| Seguridad | gitleaks, Trivy, npm audit, ZAP, sonda | CI |

## Entornos y datos
Local con Docker Compose (MySQL, backend, nginx). El CI levanta el mismo sistema en cada push. Datos: usuarios y catálogo de desarrollo que carga el backend al arrancar (`admin@petshop.com`, `cliente@ejemplo.com`); los usuarios de las pruebas se inventan en cada corrida.

## Criterios de entrada y salida
**Entrada:** compila, el sistema levanta con `docker compose up`, secretos fuera del repo.
**Salida:** CI en verde (7 trabajos + escaneos); cobertura de negocio ≥ 80 % líneas y ≥ 70 % ramas (lo exige el build); mutación ≥ 60 % (lo exige el build); p95 < 500 ms y errores < 1 % bajo 50 usuarios; accesibilidad ≥ 90; 0 defectos bloqueantes o críticos abiertos; la suite da el mismo resultado 3 veces.

## Riesgos priorizados (probabilidad × impacto)
| Riesgo | P | I | Cubierto por |
| --- | --- | --- | --- |
| Vender de más o cobrar mal | Alta | Crítico | Concurrencia (`concurrencia_stock.py`, MySQL real con 30 hilos), estados parametrizados, idempotencia, pagos |
| Acceso a datos o funciones ajenas | Media | Crítico | BOLA/BFLA en sonda y Newman, 28 operaciones de administrador con `@AdminOnly` |
| Pérdida de eventos o pagos pendientes | Media | Alto | Outbox, consumidor idempotente, DLQ, broker real, worker de pendientes |
| Falla de la pasarela | Alta | Alto | Timeout 5 s con cancelación, SOAP Fault, pool acotado |
| Inyección o HTML en entradas | Media | Alto | Filtro de HTML, SQL en sonda, XXE, validaciones |
| Dependencias vulnerables | Media | Alto | Trivy, npm audit, ZAP semanal |
| Accesibilidad y uso en celular | Media | Medio | axe, Lighthouse, responsive |
| Pruebas inestables | Media | Medio | 3 corridas seguidas (backend y frontend) |

## Brechas conocidas
Matriz de trazabilidad por HU y casos en Gherkin (faltan las HU); SonarCloud; envío de emails real; pruebas de concurrencia con MercadoPago real.
