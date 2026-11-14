# Declaración y plan de uso de IA

## 8.1 IA usada para testear
| Herramienta | Para qué | Cómo se verificó |
| --- | --- | --- |
| Claude Code (modelo Claude Sonnet 5.5, `claude-sonnet-5-5`), aplicación de escritorio | Escribir y ejecutar pruebas (unitarias, E2E, API, carga, seguridad), configurar el CI, diagnosticar fallas y corregir defectos, con revisión del equipo | Toda prueba se ejecutó localmente y en el CI; el build exige cobertura y mutación; los fallos del CI se leyeron en las anotaciones |
| Playwright, axe, Lighthouse, ZAP, Schemathesis, Trivy, gitleaks, k6, PIT | Herramientas de prueba (no son IA generativa) | Sus resultados son los de este informe |

**Prompt representativo (resumido):** "Revisá la rúbrica de aprobación y verificá qué cumple el proyecto; seguí con el punto 3 (headers, 401/403, timeouts, DLQ, ETag, BOLA, XXE)".

**Qué se aceptó:** pruebas con comportamiento verificable (acceso, límites, estados, concurrencia), la sonda de seguridad, la colección de Postman generada desde una tabla, el CI.
**Qué se corrigió o descartó, y cómo se detectó:**
- La expresión regular del filtro de HTML rechazaba textos legítimos como `"a < b"`: la detectó una prueba propia y se corrigió.
- Una prueba de persistencia del broker falla con Java 26 por un problema de Netty, no de la aplicación: se limitó a Java ≤ 24 con la justificación escrita en el código y corre en el CI (Java 21).
- Selectores de Playwright inventados al principio (botón "Ingresar"): los detectó la ejecución real y se corrigieron.
- **Error grave de la IA:** se corrió Schemathesis con token de administrador contra la base local de desarrollo; vació las preguntas frecuentes y creó datos basura. Se detectó por la salida del propio contrato, se restauró desde una copia y se fijó la regla de correr esas herramientas solo contra el sistema efímero del CI.
- Primer intento de la sonda: varias "fallas" eran errores de la propia sonda (cuerpos mal formados, comparaciones erróneas); se separaron de los defectos reales revisando el código.

**Responsabilidad:** todo test generado con IA debe ser revisado y firmado por un integrante (autor en Git) que pueda explicarlo en la defensa. **Cada integrante tiene que revisar los tests que firma antes de la presentación.**

## 8.2 IA por usar (plan)
| Herramienta / uso | Objetivo medible | Riesgo que controla |
| --- | --- | --- |
| Generación de casos de prueba a partir de las HU en Gherkin | 100 % de las HU con ≥ 3 criterios y ≥ 40 % de casos negativos o de borde | Huecos en la trazabilidad |
| Triage de defectos con IA sobre los reportes del CI | Reducir el tiempo de diagnóstico de un fallo del CI a < 15 min | Fallos que se arrastran |
| Pruebas visuales automáticas (capturas por viewport) | 0 regresiones visuales en 360/768/1280 px | Cambios de estilo no detectados |
| Revisión defensiva del código con IA antes de cada entrega | 0 hallazgos críticos nuevos por entrega | Defectos de seguridad tardíos |

Toda salida de IA se acepta solo con una prueba ejecutada que la respalde.

## 8.3 Funcionalidad de IA del sistema
Salchi Bot **no usa un modelo de lenguaje**: es determinista (`ChatAdvisor` y `FaqMatcher`, ver el código y la Javadoc), funciona sin conexión y sin claves. Por eso no aplican la inyección de prompt ni la fuga de datos de un modelo. Sus reglas están cubiertas por pruebas unitarias y por el E2E de preguntas frecuentes.
