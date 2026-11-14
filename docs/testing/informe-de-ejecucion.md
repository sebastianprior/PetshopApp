# Informe de ejecución

Fuente: corridas del CI del commit `dc96132` (rama `feature/ci-y-compose`) y mediciones locales del 13/11/2026. Pipeline principal: **6 min 33 s** (límite 15 min).

## Totales por nivel
| Nivel | Ejecutadas | Pasadas | Falladas | Omitidas |
| --- | --- | --- | --- | --- |
| Backend (unitario, componente, broker real, MySQL real) | 463 | 463 | 0 | 0 en el CI (12 en una máquina sin Docker y con Java 26) |
| Frontend (Vitest) | 159 | 159 | 0 | 0 |
| E2E Playwright | 40 | 40 | 0 | 0 |
| API Newman | 203 solicitudes / 224 aserciones | todas | 0 | 0 |
| Sonda de seguridad y resiliencia | 79 | 79 | 0 | 0 |
| Concurrencia de stock (12 compradores) | 1 | 1 | 0 | 0 |
| Contrato Schemathesis | 902 casos × 3 comprobaciones | todos | 0 | 0 |

Estabilidad: la suite del backend corrió 3 veces seguidas con idéntico resultado (463 / 0 / 0); la del frontend también. Una prueba inestable apareció en el CI (ver D-14) y se corrigió.

## Cobertura y mutación (mediciones del 13/11)
| Medida | Resultado | Umbral |
| --- | --- | --- |
| Líneas, capa de negocio (`service` + `payment`) | 93,3 % | ≥ 80 % |
| Ramas, capa de negocio | 82,0 % | ≥ 70 % |
| Proyecto completo | 82,8 % líneas / 72,1 % ramas | — |
| Mutación (PIT) | 79,7 % (675 de 847 mutantes) | ≥ 60 % |

El build falla si cae por debajo de los umbrales. Se agregaron pruebas después de esta medición (devoluciones, concurrencia); la próxima corrida de PIT y de JaCoCo actualiza las cifras.

## Rendimiento (k6, 50 usuarios, 5 minutos, runner de GitHub)
| Endpoint | p95 | Mediana | Errores |
| --- | --- | --- | --- |
| Catálogo `GET /api/products` | 3 ms | 1 ms | 0 % |
| Login `POST /api/auth/login` | 86 ms | 57 ms | 0 % |
| Checkout `POST /api/v1/ventas` | 33 ms | 8 ms | 0 % |

45.480 pedidos en 14.662 iteraciones. Criterio: p95 < 500 ms y errores < 1 %.

## Accesibilidad y responsive
Lighthouse (móvil): accesibilidad **100** en inicio, productos, carrito e ingreso; mejores prácticas 100; SEO 91–92; rendimiento 55–64 (emulación móvil con limitación de CPU: queda como mejora). axe sin violaciones críticas ni serias en 6 páginas. Sin scroll horizontal a 360, 768 y 1280 px.

## Pendiente de este informe
Captura del Quality Gate de SonarCloud (todavía no configurado).
