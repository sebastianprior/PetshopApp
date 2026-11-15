# Guion de la 2.ª Entrega Parcial (16/11): núcleo funcionando en vivo

Cada punto de la consigna, qué mostrar y dónde está en el código. Todo se muestra funcionando, no con capturas.

## Antes de empezar (10 minutos antes del turno)

### Opción A: sin Docker (la que se usa en la demo de esta entrega)
Requisitos: Java 21 o superior, Node 22 o superior y MySQL en `localhost:3306` (el servicio `MySQL80` arranca solo con Windows;
la base y el usuario de desarrollo los crea `setup-petshop-db.sql` la primera vez).

Abrir **tres terminales** (PowerShell) en la carpeta del proyecto:

```powershell
# Terminal 1: backend (puerto 8080). Esperar la línea "Started AppApplication".
cd app
.\mvnw.cmd spring-boot:run

# Terminal 2: tienda (puerto 5173)
cd app\src\app
npm install        # solo la primera vez
npm run dev

# Terminal 3: libre, para los comandos de la demo (curl)
```
Los mensajes de la cola (`[COLA ventas.confirmadas] ...`) se ven en la Terminal 1.

### Opción B: con Docker (un solo comando; para la entrega final)
```bash
docker compose up --build        # desde la raíz; la primera vez tarda unos minutos
```
Requiere [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/). Los mensajes se ven con `docker compose logs -f backend`.

### En ambas opciones
- Tienda: <http://localhost:5173> · API: <http://localhost:8080/api> · Swagger: <http://localhost:8080/swagger-ui.html>
- Usuarios de desarrollo: `admin@petshop.com` / `admin123` (administrador) y `cliente@ejemplo.com` / `password` (cliente).

## 1. Componentes en capas y primer flujo de punta a punta (3 min)
- Mostrar [ADR 0001](../adr/0001-arquitectura-en-capas.md): `controller → service → repository → model`.
- En la tienda: ingresar como cliente, agregar productos al carrito, elegir envío por código postal y **pagar con la tarjeta de prueba aprobada**.
  Resultado: pantalla «Compra confirmada».
- En la terminal de logs aparece el evento de la venta en la cola (`[COLA ventas.confirmadas] VentaConfirmada ventaId=...`): eso es la **mensajería funcionando**.

## 2 y 3. Patrón State (409) y seguridad por rol (401/403) — [ADR 0003](../adr/0003-patron-state-en-la-venta.md) y [ADR 0011](../adr/0011-jwt-y-control-de-acceso-por-rol.md) (5 min)
En la Terminal 3, desde la carpeta `app`:
```powershell
python tests-api\demo_entrega2.py
```
Hace las 10 comprobaciones y muestra cada una con su código HTTP (necesita al menos un pedido `COMPLETADA`: si no hay, hacer una compra en la tienda con la tarjeta de prueba aprobada):

| Comprobación | Resultado esperado |
| --- | --- |
| `GET /api/orders` sin sesión, con token alterado | **401** |
| `GET /api/orders` con la sesión de un cliente | **403** |
| `GET /api/orders` con la sesión del administrador | **200** |
| Venta `COMPLETADA` → `ENVIADA` | **200** |
| `ENVIADA` → `ENVIADA` y `ENVIADA` → `CANCELADA` | **409** `application/problem+json`, `urn:petshop:problem:transicion-invalida` |
| Estado que no existe | **422** |
| La ruta vieja del panel (`PUT /api/orders/{id}/status`) con una transición inválida | **409** (antes aceptaba cualquier cambio) |

- En el panel de administrador, el desplegable de un pedido enviado o cancelado dice «Sin cambios posibles».
- Código: `model/EstadoVenta.java` (la tabla de transiciones) y `EstadoVentaTest` (12 combinaciones: 4 permitidas y 8 rechazadas).
- El script consume un pedido `COMPLETADA` cada vez que se corre (lo pasa a `ENVIADA`): para repetir la demo, hacer otra compra.

## 4. Patrones implementados, cada uno con su ADR (2 min)
| Patrón | Dónde está | ADR |
| --- | --- | --- |
| State | `model/EstadoVenta` | [0003](../adr/0003-patron-state-en-la-venta.md) |
| Strategy | `service/ProductSortStrategy` (`PriceAscStrategy`, `PriceDescStrategy`); en vivo: `GET /api/products?sort=menorprecio` y `?sort=mayorprecio` | [0004](../adr/0004-patron-strategy-en-el-orden-de-productos.md) |
| Adapter | `payment/PaymentGateway` → `SoapPaymentGateway`; `MercadoPagoClient` → `HttpMercadoPagoClient` | [0005](../adr/0005-patron-adapter-en-las-pasarelas-de-pago.md) |
| Outbox | `service/OutboxRelay`, tabla `outbox_events` | [0006](../adr/0006-outbox-para-los-eventos-de-venta.md) |
| Consumidor idempotente + DLQ | `service/VentaEventListener`, `DlqService` | [0007](../adr/0007-consumidor-idempotente-y-dlq.md) |

## 5. Primeros tests unitarios (1 min)
```powershell
cd app
.\mvnw.cmd test              # 490+ pruebas; la cobertura queda en target\site\jacoco\index.html (en Linux o Mac: ./mvnw test)
```
El CI los corre en cada push (GitHub Actions) y el resultado está en el Pull Request.

## Lo que falta confirmar fuera del código
- **Jira al día:** historias de usuario con su estado real, tareas de esta entrega y defectos (el [registro](../testing/registro-de-defectos.md) tiene los 17 con causa y cierre para cargarlos).
- **Feedback del diseño incorporado:** llevar la lista de observaciones del 03/11 y señalar dónde se corrigió cada una.
- Llegar con el sistema ya levantado: el primer `docker compose up --build` compila todo y tarda varios minutos.
