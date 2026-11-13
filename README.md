# PetshopApp

Tienda online para mascotas: catálogo, carrito, checkout con envío por código postal, panel de administración,
preguntas frecuentes y Salchi Bot (asistente de recomendaciones).

- **Backend** (`app/`): Spring Boot, Java 21 y MySQL.
- **Frontend** (`app/src/app/`): React 18, TypeScript y Vite.

## Levantar todo con un solo comando (Docker)

Necesitás [Docker](https://docs.docker.com/get-docker/) con Compose. Desde la raíz del repositorio:

```bash
docker compose up --build
```

La primera vez tarda unos minutos (compila el backend y la tienda). Cuando termina:

| Qué | Dónde |
| --- | --- |
| Tienda | <http://localhost:5173> |
| API | <http://localhost:8080/api> |
| Documentación de la API (Swagger) | <http://localhost:8080/swagger-ui.html> |

El comando levanta MySQL, el backend (que trae adentro el broker de mensajes Artemis y la pasarela de pago SOAP simulada) y
la tienda. Los datos de ejemplo y los usuarios de desarrollo se cargan solos (ver abajo). Para apagar todo:
`docker compose down` (agregá `--volumes` para borrar también la base). Opcional: `JWT_SECRET=<32 o más caracteres>` para que
las sesiones sobrevivan a un reinicio, y `DB_PASSWORD` para cambiar la contraseña de la base.

## Cómo levantarlo en local, sin Docker

1. **Base de datos.** Necesitás MySQL en `localhost:3306`. El script `setup-petshop-db.sql` crea la base y el usuario de desarrollo.
2. **Backend** (puerto 8080), desde `app/`:

   ```bash
   ./mvnw.cmd spring-boot:run      # en Linux o Mac: ./mvnw spring-boot:run
   ```

3. **Frontend** (puerto 5173), desde `app/src/app/`:

   ```bash
   npm install
   npm run dev
   ```

La tienda queda en <http://localhost:5173>. Al arrancar, el backend carga categorías, productos, preguntas frecuentes y
zonas de envío de ejemplo, y crea usuarios de **desarrollo**: `admin@petshop.com` / `admin123` (administrador) y
`cliente@ejemplo.com` / `password` (cliente). No uses esas credenciales en un entorno real.

## Tests

```bash
./mvnw.cmd test                      # backend, desde app/ (en Linux o Mac: ./mvnw test)
npm test                             # frontend, desde app/src/app/
npx tsc --noEmit                     # revisión de tipos del frontend
```

En cada push, GitHub Actions (`.github/workflows/ci.yml`) compila y prueba el backend (con control de cobertura), revisa los
tipos y prueba el frontend, corre las pruebas de mutación (PIT), escanea el historial en busca de secretos (gitleaks) y
levanta el sistema completo con `docker compose` para comprobar que responde.

Con la aplicación en ejecución, la sonda de seguridad ataca la API real (BOLA, BFLA, JWT alterado, mass assignment,
inyecciones, XXE, fuerza bruta, headers, CORS, ETag/412, estados y cola de mensajes muertos):

```bash
python tests-api/sonda_seguridad.py     # desde app/
```

## Seguridad y resiliencia

- **Headers.** Toda respuesta lleva `Content-Security-Policy`, `Strict-Transport-Security`, `X-Content-Type-Options`,
  `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`; lo que depende de la sesión, además, `Cache-Control: no-store`.
- **Sesión.** Sin una sesión válida (sin token, vencido, con la firma alterada o de otro emisor) las operaciones de
  administrador responden **401**; con la sesión de un cliente, **403**. El rol se controla antes de leer el cuerpo.
- **Entrada.** Los textos con etiquetas HTML (`<script>`…) se rechazan con 400; las contraseñas no se revisan.
- **Pagos.** La llamada a la pasarela vence a los 5 segundos y se interrumpe; si se vence o la pasarela está saturada, la venta
  queda `PAGO_PENDIENTE` con el stock reservado y un proceso la reintenta (con tope de intentos).
- **Mensajería.** Cada evento de venta se guarda primero en la tabla `outbox_events` y un proceso lo entrega al broker con
  reintentos: si el broker está caído, no se pierde. El consumidor descarta duplicados (`eventId`). Un mensaje que falla 3
  veces pasa a la cola `DLQ`; el administrador la ve con `GET /api/admin/dlq` y la reprocesa con `POST /api/admin/dlq/reprocess`.
- **Concurrencia.** `GET /api/v1/ventas/{id}` y `GET /api/products/{id}` devuelven un `ETag`; `PATCH /api/v1/ventas/{id}` y
  `PUT /api/products/{id}` aceptan `If-Match` y responden **412** si el recurso cambió desde que se leyó.

## Variables de entorno

### Backend

| Variable | Para qué sirve | Si no se define |
| --- | --- | --- |
| `JWT_SECRET` | Clave con la que se firman los tokens (mínimo 32 caracteres). **Obligatoria en producción.** | Se genera una al azar en cada arranque: las sesiones se pierden al reiniciar. |
| `DB_URL` | Dirección JDBC de MySQL. | `jdbc:mysql://localhost:3306/petshopdb?...` |
| `DB_USERNAME` | Usuario de la base. | `petshop` |
| `DB_PASSWORD` | Contraseña de la base. **Definila en producción.** | `petshop123` (solo desarrollo) |
| `MP_ACCESS_TOKEN` | Access Token de MercadoPago (**secreto**; empieza con `TEST-` en pruebas). Sin él, MercadoPago no se ofrece. | Vacío: solo los medios de prueba. |
| `FRONTEND_URL` | Dirección de la tienda: a donde MercadoPago devuelve al cliente después de pagar. | `http://localhost:5173` |
| `PUBLIC_API_URL` | Dirección pública del backend (por ejemplo `https://mi-api.onrender.com`), para que MercadoPago avise los pagos (webhook). | Vacío: sin webhook; igual se confirma al volver y con el worker. |
| `SIMULATED_PAYMENTS` | `false` apaga los medios de prueba (tarjeta aprobada/rechazada…). **Ponelo en `false` en producción**: la pasarela simulada aprueba cualquier cosa. | `true` |
| `MP_PENDING_EXPIRY_MINUTES` | Minutos que un pedido espera el pago antes de cancelarse y liberar el stock. | `30` |
| `GOOGLE_CLIENT_ID` | Client ID de Google para "Continuar con Google" (es público). Vacío = el botón no se muestra. | El de este proyecto (ver abajo). |
| `CORS_ALLOWED_ORIGINS` | Direcciones del frontend que pueden llamar a la API, separadas por coma (por ejemplo `https://mi-tienda.vercel.app`). | `http://localhost:5173,http://localhost:4173` |
| `ARTEMIS_PERSISTENT` | `true` guarda en disco los mensajes del broker (sobreviven a un reinicio). Lo activan el `docker-compose.yml` y el `Dockerfile`. | `false` (en desarrollo local con Java 25 o más el broker no puede releer su journal) |
| `ARTEMIS_DATA_DIR` | Carpeta del journal del broker. | `artemis-data` |

### Frontend

| Variable | Para qué sirve | Si no se define |
| --- | --- | --- |
| `VITE_API_URL` | Dirección de la API, incluido `/api` (por ejemplo `https://mi-backend.onrender.com/api`). Se lee **al compilar** (`npm run build`). | `http://localhost:8080/api` |

Hay un ejemplo en `app/src/app/.env.example`. Los archivos `.env` no se suben al repositorio.

## Pagos con MercadoPago

La tienda usa Checkout Pro: el cliente elige "MercadoPago" en el carrito, paga en el sitio de MercadoPago y vuelve a la tienda
(`/pago/resultado`). El pedido queda **pago pendiente** (con el stock reservado) hasta que MercadoPago confirma el pago. La
confirmación nunca sale de lo que diga el navegador: el backend le pregunta a MercadoPago por los pagos del pedido y solo
confirma si hay uno **aprobado por el monto exacto**. Se actualiza de tres formas: al volver el cliente, por el aviso
(webhook `POST /api/payments/mercadopago/webhook`) y por un proceso que revisa los pendientes cada 15 segundos. Si el pago no
se completa en `MP_PENDING_EXPIRY_MINUTES`, el pedido se cancela y se libera el stock; cancelar un pedido ya pagado devuelve el
dinero por la API de MercadoPago.

Para probarlo:
1. En [MercadoPago Developers](https://www.mercadopago.com.ar/developers) creá una aplicación y copiá el **Access Token de prueba**
   (`TEST-...`). Definilo como variable de entorno `MP_ACCESS_TOKEN` (nunca en el código ni en el chat).
2. Creá dos usuarios de prueba (comprador y vendedor) desde el mismo panel y pagá con el comprador de prueba.
3. En local no hace falta el webhook: al volver a la tienda y por el proceso de pendientes el pago se confirma igual. En un
   servidor público, definí `PUBLIC_API_URL` para que MercadoPago también avise.

## Ingreso con Google

El botón "Continuar con Google" usa Google Identity Services: el navegador recibe un token firmado por Google y el backend lo
verifica (firma, aplicación, vencimiento y email verificado) antes de abrir la sesión; la tienda nunca ve la contraseña de Google.
Quien entra por primera vez recibe una cuenta de cliente. Si ya existe una cuenta con ese email creada con contraseña, no se
mezclan (así nadie puede adueñarse de una cuenta ajena con un email que no verificó).

En [Google Cloud Console](https://console.cloud.google.com) → APIs y servicios → Credenciales, el ID de cliente de OAuth (tipo
"Aplicación web") tiene que listar en **Orígenes autorizados de JavaScript** cada dirección desde la que se abre la tienda:
`http://localhost:5173` para desarrollo y la dirección pública (por ejemplo `https://mi-tienda.vercel.app`) al publicar.
Mientras la pantalla de consentimiento esté en modo "Prueba", solo pueden ingresar los mails cargados en **Usuarios de prueba**.

## Publicarlo

- **Frontend (Vercel o similar).** Carpeta raíz del proyecto: `app/src/app`. Comando de build: `npm run build`, salida: `dist`.
  Definí `VITE_API_URL` en el panel del servicio. La tienda usa direcciones reales (`/productos/p1`, `/carrito`…), así que el
  hosting tiene que devolver `index.html` para cualquier ruta: `vercel.json` ya lo hace en Vercel.
- **Backend (Render, Railway, etc.).** Hay un `Dockerfile` en `app/` (en Render: lenguaje Docker, carpeta raíz `app`). Definí
  `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `CORS_ALLOWED_ORIGINS` con la dirección del frontend. La plataforma
  define `PORT` y la app lo respeta. Sin Docker: `./mvnw.cmd package` y `java -jar target/app-0.0.1-SNAPSHOT.jar` con Java 21.
- Los emails (por ejemplo, el código para recuperar la contraseña) hoy se guardan como archivos en la carpeta `outbox/`: es un
  envío simulado. Para producción hay que reemplazarlo por un envío real.
- Las imágenes que sube el administrador se guardan en `app/uploads/`: en un servicio con disco efímero se pierden al reiniciar.
