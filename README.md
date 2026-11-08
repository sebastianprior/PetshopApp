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
| `CORS_ALLOWED_ORIGINS` | Direcciones del frontend que pueden llamar a la API, separadas por coma (por ejemplo `https://mi-tienda.vercel.app`). | `http://localhost:5173,http://localhost:4173` |

### Frontend

| Variable | Para qué sirve | Si no se define |
| --- | --- | --- |
| `VITE_API_URL` | Dirección de la API, incluido `/api` (por ejemplo `https://mi-backend.onrender.com/api`). Se lee **al compilar** (`npm run build`). | `http://localhost:8080/api` |

Hay un ejemplo en `app/src/app/.env.example`. Los archivos `.env` no se suben al repositorio.

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
