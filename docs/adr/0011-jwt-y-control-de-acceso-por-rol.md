# ADR 0011: JWT y control de acceso por rol (401 vs 403)

- **Estado:** Aceptada
- **Fecha:** 2026-11-15
- **Tipo:** Seguridad

## Contexto
La API la usan clientes (comprar, ver sus pedidos) y administradores (catálogo, pedidos, FAQ, cola de mensajes muertos). Hay que
identificar a la persona sin guardar sesiones en el servidor y distinguir «no sé quién sos» de «sé quién sos pero no podés».

## Decisión
- **Autenticación con JWT** firmado con HMAC (clave de `JWT_SECRET`, vencimiento de **15 minutos como máximo**: el arranque falla si se configura más) enviado en el header `X-Auth-Token`, más
  un *refresh token* guardado con hash para renovar la sesión. Contraseñas con **BCrypt**.
- **Autorización por rol** (`CUSTOMER`, `ADMIN`): las operaciones de administrador llevan `@AdminOnly` y un interceptor controla
  el rol **antes de leer el cuerpo**; así un cuerpo incompleto no puede esconder un acceso indebido.
- **Códigos:** sin token, vencido, con firma alterada, `alg=none` o de otro emisor → **401**; con la sesión de un cliente en una
  ruta de administrador → **403** (`AdminGuard.denied`).
- **Propiedad de los recursos:** un usuario solo ve y modifica sus ventas, pagos y devoluciones (protección contra BOLA/IDOR).
- **Fuerza bruta:** desde el 6.º intento fallido de ingreso se responde **429** (`LoginRateLimiter`).

## Alternativas descartadas
- **Sesión en servidor (cookie):** exige almacenamiento compartido entre instancias y añade riesgo de CSRF.
- **Roles solo en el front:** se saltan llamando a la API directamente.
- **OAuth completo con un servidor de identidad propio:** exceso; el ingreso con Google (token verificado en el backend) cubre el
  caso externo.

## Consecuencias
- (+) El servidor no guarda sesión; el control de rol es uniforme y probado.
- (−) Un JWT no se puede revocar antes de vencer; se mitiga con vencimiento corto y refresh token revocable.

## Implementación y evidencia
- `service/JwtUtil`, `RefreshTokenService`, `AdminGuard`, `LoginRateLimiter`; `config/AdminOnly`, `AdminOnlyInterceptor`,
  `AdminOnlyConfig`.
- Pruebas: `JwtUtilTest`, `AdminGuardDeniedTest` (vencido, firma alterada, `alg=none`, otro emisor), `AdminOnlyInterceptorTest`,
  `LoginRateLimiterTest`; sonda de seguridad (BOLA, BFLA, 7 variantes de JWT, fuerza bruta) y casos de Newman.
