# PetshopApp

Tienda online para mascotas: catálogo, carrito, checkout con envío por código postal, panel de administración,
preguntas frecuentes y Salchi Bot (asistente de recomendaciones).

- **Backend** (`app/`): Spring Boot, Java 21 y MySQL.
- **Frontend** (`app/src/app/`): React 18, TypeScript y Vite.

## Cómo levantarlo en local

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
./mvnw.cmd test                      # backend, desde app/
npm test                             # frontend, desde app/src/app/
```

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
