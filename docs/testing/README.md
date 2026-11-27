# Documentación de testing (rúbrica Clase 15)

Cada archivo se escribió con los resultados reales de las corridas del CI (`.github/workflows/ci.yml` y `seguridad.yml`).

| Entregable de la rúbrica | Archivo | Estado |
| --- | --- | --- |
| 1. Plan y estrategia de pruebas | [plan-de-pruebas.md](plan-de-pruebas.md) | Listo |
| 2. Matriz de trazabilidad | [matriz-de-trazabilidad.md](matriz-de-trazabilidad.md) | Lista (24 HU y 98 casos; **los IDs de HU son provisorios**: alinear con la lista del 03/11 y con Jira) |
| 3. Especificación de casos | [historias-y-casos-gherkin.md](historias-y-casos-gherkin.md) | Lista (Gherkin; los casos automatizados están en `app/src/test`, `app/src/app/e2e` y `app/tests-api`) |
| 4. Informe de ejecución | [informe-de-ejecucion.md](informe-de-ejecucion.md) | Listo (falta la captura del Quality Gate de SonarCloud) |
| 5. Registro de defectos | [registro-de-defectos.md](registro-de-defectos.md) | Listo (19 defectos cerrados y 8 abiertos declarados; falta vincular los IDs de Jira) |
| 6. Informe de seguridad | [informe-de-seguridad.md](informe-de-seguridad.md) | Listo |
| 7. Declaración y plan de IA | [declaracion-de-ia.md](declaracion-de-ia.md) | Listo (cada integrante debe revisarla y firmar sus tests) |
| 8. Lecciones aprendidas | al final de [registro-de-defectos.md](registro-de-defectos.md) | Listo |

Pendientes que no se resuelven desde el código: SonarCloud (`SONAR_TOKEN` y captura del Quality Gate), tag `v1.0-final` en `main`, Jira (casos y defectos con estado real). El [manual de usuario](../manual-de-usuario.md) está en `docs/`.

## Cómo reproducir todo

```bash
docker compose up --build                       # sistema completo
python app/tests-api/sonda_seguridad.py         # 79 pruebas de seguridad y resiliencia
python app/tests-api/concurrencia_stock.py      # sobreventa con compradores simultáneos
npx newman run app/tests-api/petshop.postman_collection.json --working-dir app/tests-api
cd app/src/app && npx playwright test           # E2E, responsive y accesibilidad
cd app && ./mvnw verify                         # backend (con cobertura) ; ./mvnw org.pitest:pitest-maven:mutationCoverage
```
