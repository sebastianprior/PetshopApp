# ADR 0002: API REST versionada con errores en Problem Details (RFC 9457)

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** API

## Contexto
La tienda y los clientes de prueba (Postman, k6, Schemathesis) necesitan errores que se puedan interpretar sin adivinar el
texto, con un contrato estable y documentado.

## Decisión
- La API de ventas y productos va bajo `/api/v1/...` y está documentada con OpenAPI 3.0 (`/v3/api-docs`, Swagger UI).
- Los errores de la API v1 se devuelven como `application/problem+json` (RFC 9457) con `type` (`urn:petshop:problem:<slug>`),
  `title`, `status` y `detail`. Los `slug` son estables (`transicion-invalida`, `precondicion-fallida`, `pago-rechazado`…).
- Códigos con significado fijo: 400/422 datos inválidos, 401 sin sesión válida, 403 sin permiso, 404 inexistente,
  **409 transición de estado inválida**, 412 ETag desactualizado, 429 demasiados intentos.

## Alternativas descartadas
- **`{"error": "texto"}` ad hoc:** cada endpoint inventa su forma y los clientes dependen del texto.
- **Códigos 200 con el error en el cuerpo:** rompe caché, métricas y herramientas de prueba de contrato.

## Consecuencias
- (+) Un cliente puede decidir por `status` y `type` sin leer el texto; Schemathesis valida el contrato (902/902 en el CI).
- (−) Convive con endpoints históricos (`/api/...`) que todavía responden `{"error": ...}`; los nuevos y los migrados usan
  Problem Details (`Problems.of`).

## Implementación y evidencia
- `controller/Problems.java`, `GlobalExceptionHandler` (traduce `TransicionInvalidaException` a 409).
- `config/OpenApiConfig`, `springdoc.api-docs.version=openapi_3_0`.
- Colección Postman/Newman con un caso de éxito y uno de error por operación; Schemathesis en `seguridad.yml`.
