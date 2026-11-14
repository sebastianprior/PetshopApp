# Informe de seguridad

Criterios del eje D de la rúbrica (base: OWASP ASVS, API Security Top 10, Ley 25.326). Estado: **CUMPLE**, **NO CUMPLE** o **NO VERIFICABLE**, siempre con evidencia ejecutada. Un "cumple" sin prueba se toma como no verificable.

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Usuario A pide un recurso de B (BOLA/IDOR) | CUMPLE | Sonda (6 pruebas): venta, pago, sincronización, cancelación, modificación y listados de B no muestran lo de A (404/403). Newman: "Otro usuario no ve mi venta" |
| Cliente invoca endpoint de administrador (BFLA) | CUMPLE | Sonda (15 operaciones, 403) y Newman (un caso por operación de administrador). 28 operaciones con `@AdminOnly`, controladas antes de leer el cuerpo |
| JWT vencido, con firma alterada, `alg=none`, de otro emisor | CUMPLE | Sonda: 7 variantes × 2 rutas = 401. `AdminGuardDeniedTest`: token vencido real, firma alterada, `alg=none`, otro emisor |
| Mass assignment (rol, id, estado, precio) | CUMPLE | Sonda: `role=ADMIN` y `id` en el registro y en `PUT /auth/me` se ignoran; el precio del carrito lo fija el servidor; `userId`/`estado` inyectados en la venta se ignoran |
| Inyección SQL | CUMPLE | Sonda: login y búsqueda con `' OR 1=1`; el catálogo sigue completo. Consultas parametrizadas (JPA) |
| XSS en campos de texto | CUMPLE (con alcance) | Filtro `HtmlInputFilter`: las etiquetas en cualquier texto de un cuerpo JSON se rechazan con 400 (15 pruebas). Alcance: cuerpos JSON; las contraseñas no se revisan |
| XXE en el canal SOAP | CUMPLE | Sonda y Newman: entidad `file://`, `http://` y "billion laughs" no se expanden ni se filtran. Depende de los valores por defecto del analizador XML (no hay configuración propia): conviene fijarla explícitamente |
| Fuerza bruta en login | CUMPLE | Sonda: desde el 6.º intento fallido responde 429 |
| ZAP (API activo autenticado y tienda baseline) | CUMPLE | 0 alertas High. Medium justificadas abajo |
| Dependencias | CUMPLE (con una excepción documentada) | Trivy: 0 HIGH/CRITICAL (backend y frontend). `npm audit` de producción: 0. De desarrollo: 5 (Vite y vitest) sin llegar al paquete publicado |
| Headers de seguridad | CUMPLE | API: CSP, HSTS, `nosniff`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` (sonda). Tienda: los mismos en nginx y `vercel.json`; CSP verificada en un navegador real (E2E-15, sin violaciones) |
| CORS | CUMPLE | Sonda: origen permitido reflejado exacto, ajeno sin `Access-Control-Allow-Origin`, preflight ajeno rechazado, nunca comodín con credenciales |
| Cookies | NO APLICA | La sesión viaja en el header `X-Auth-Token`; el sistema no usa cookies |
| Errores sin trazas | CUMPLE | Sonda: cuerpo malformado, tipos erróneos y rutas inexistentes no muestran trazas ni clases internas; `spring.web.error.include-*=never` |
| Sin datos personales ni tokens en logs | CUMPLE (sesión de pruebas) | El log del backend tras la sesión de pruebas no contiene ningún JWT, token de MercadoPago ni contraseña (búsqueda por patrón) |
| Cifrado en tránsito (TLS) | NO VERIFICABLE | En local es HTTP. Render y Vercel sirven HTTPS al publicar; verificar con el sistema publicado |
| Contraseñas con hash adaptativo | CUMPLE | BCrypt (`DatabaseSeederTest`, `SensitiveDataTest`); la API nunca devuelve la contraseña |
| Secretos fuera del repositorio | CUMPLE (con valores de desarrollo declarados) | gitleaks sobre todo el historial en cada push. Se declaran como falsos positivos `petshop123` y `admin123` (desarrollo), un test con datos inventados y contraseñas descartables de pruebas viejas |

## Alertas Medium de ZAP, una por una
| Alerta | Dónde | Tratamiento |
| --- | --- | --- |
| ZAP is Out of Date | API y tienda | Es un aviso sobre la herramienta, no sobre el sistema |
| CSP Header Not Set en `/v3/api-docs` | API | Falso positivo: el header está presente (verificado con `curl -I`); la documentación usa una CSP propia |
| CSP: `style-src 'unsafe-inline'` | Tienda | Aceptado: React usa estilos en línea. La política no permite scripts en línea ni orígenes externos salvo Google (ingreso) y Unsplash (fotos) |
| Sub Resource Integrity Attribute Missing | Tienda | Resuelto: las fuentes de Google se sirven desde la propia tienda |

## Hallazgos y su tratamiento
Los defectos de seguridad encontrados y corregidos son D-01, D-02, D-03, D-05 a D-08 y D-10 del [registro de defectos](registro-de-defectos.md). Abierto: credenciales de desarrollo del sembrado (mayor, con mitigación).

## Qué no se verificó
TLS del sistema publicado; escaneo de ZAP con sesión de cliente (solo se escaneó con la de administrador); XXE con configuración explícita del analizador.
