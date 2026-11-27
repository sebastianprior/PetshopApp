# Manual de usuario — PetshopApp

Tienda online de productos para mascotas. Este manual tiene dos partes: la de **quien compra** (cliente o invitado) y la de **quien administra** la tienda.

- **Tienda:** https://petshop-app-five.vercel.app/
- **Panel de administración:** https://petshop-app-five.vercel.app/admin (solo para administradores)
- **Cómo levantarla en tu computadora:** ver el [README](../README.md) (`docker compose up --build`: la tienda queda en http://localhost:5173 y la API en http://localhost:8080).

> La primera vez que se abre, el servidor puede tardar hasta un minuto en responder (se «duerme» cuando no se usa). Es normal; esperá y recargá.

---

## Parte 1. Para quien compra

### 1. Entrar a la tienda
Podés **comprar sin cuenta** (como invitado) o **iniciar sesión** para ver tu historial, guardar favoritos y devolver productos.

- **Crear una cuenta o ingresar:** tocá **Ingresar** (`/ingresar`). Podés usar email y contraseña, o **ingresar con Google**.
- Si te equivocás de contraseña, la pantalla lo avisa. Después de **5 intentos fallidos seguidos** con el mismo email se bloquea el ingreso por un rato.
- Para cerrar la sesión usá **Cerrar sesión**.

### 2. Encontrar productos
- En **Productos** (`/productos`) podés **filtrar por categoría** y **buscar por nombre o marca**.
- **Ofertas** (`/ofertas`) muestra los productos con descuento y **Categorías** (`/categorias`) las agrupa.
- Al abrir un producto ves sus **variantes** (por ejemplo, el peso), el precio, el stock y las **reseñas**. Para dejar una reseña tenés que haber iniciado sesión; podés reseñar cada producto **una sola vez**, con una calificación de 1 a 5.
- Con el corazón guardás un producto en **Favoritos** (`/favoritos`); necesitás sesión.

### 3. Armar el carrito
1. En el producto o en la lista, tocá para **agregarlo al carrito** (`/carrito`).
2. Con **+** y **−** cambiás la cantidad; con **Quitar** lo sacás. Si pedís más unidades que el stock, la compra se rechaza con el aviso «Stock insuficiente».
3. Si superás el monto de **envío gratis** o el del **regalo**, el carrito lo muestra y los aplica solo.
4. **Ruleta del día:** podés girarla una vez por día para obtener un 10 % de descuento en una categoría sorteada. Se aplica solo a los productos de esa categoría.

### 4. Cupón y envío
- **Cupón:** escribí el código y tocá **Aplicar**. Solo admite letras, números, guiones y guiones bajos. Si no es válido, vencido o no llegás al mínimo, el carrito te avisa y el total no cambia.
- **Envío:** escribí tu **código postal** (4 números, por ejemplo `1425`; también se acepta el formato `C1425ABC`). Ves la zona, el costo y el plazo de entrega.

### 5. Datos de envío
Completá estos campos. Si hay un error, aparece **en rojo debajo del campo** y el formulario no se envía hasta corregirlo.

| Campo | Qué se pide |
| --- | --- |
| Nombre y apellido | Solo letras, espacios, puntos, apóstrofes y guiones (máximo 80) |
| Dirección | Calle y número, mínimo 5 caracteres (máximo 120) |
| Ciudad | Solo letras (máximo 60) |
| Código postal | 4 números |
| Teléfono | **Opcional.** Entre 6 y 15 números; podés usar espacios, guiones, paréntesis y `+` |

### 6. Pagar
En **Medio de pago** elegís cómo pagar y tocás **Finalizar compra** (o **Pagar con MercadoPago**).

- **MercadoPago:** te lleva a la página de MercadoPago, pagás y volvés con **Volver al sitio**. Verás **«¡Compra confirmada!»**.
- **Tarjeta de prueba (simulada):** la tienda está en modo demostración. Podés elegir «aprobada» (la compra se confirma), «rechazada» (no se cobra y el carrito se conserva), «datos inválidos», «pasarela lenta» (queda pendiente y se confirma) o «pasarela caída» (queda pendiente y se cancela).
- Si el pago no se completa, el stock se libera y el pedido se cancela; no se cobra dos veces aunque toques el botón más de una vez.

**Para probar MercadoPago en modo prueba** usá las cuentas de prueba y esta tarjeta: Mastercard `5031 7557 3453 0604`, código `123`, vencimiento `11/30`, titular `APRO` (aprobado) o `FUND` (fondos insuficientes), documento `12345678`.

### 7. Seguir mis pedidos y devolver
- **Mis pedidos** (`/mis-pedidos`): ves cada compra con su estado (`PAGO_PENDIENTE`, `COMPLETADA`, `ENVIADA` o `CANCELADA`), los productos y el total. Solo ves **tus** pedidos.
- **Solicitar devolución:** en un pedido, tocá **Solicitar devolución**, elegí el producto, la cantidad y el motivo. Solo podés devolver productos que compraste y no más unidades de las compradas; los regalos no se devuelven. El administrador la aprueba o la rechaza y, si la aprueba, te reembolsa.

### 8. Mi cuenta
En **Mi cuenta** (`/mi-cuenta`) cambiás tu nombre y tu contraseña (si ingresás con Google no tenés contraseña propia).

### 9. Preguntas y ayuda
- **Preguntas frecuentes** (`/preguntas-frecuentes`): tocá una pregunta para ver la respuesta.
- **Salchi Bot** (botón flotante): escribile qué necesitás, por ejemplo «algo para mi perro, hasta $20.000», y te recomienda hasta 3 productos. También responde las preguntas frecuentes. No es inteligencia artificial: funciona con reglas.
- **Contacto**, **Términos** y **Privacidad** están en el pie de la página.

---

## Parte 2. Para quien administra

### 1. Ingresar al panel
Iniciá sesión con una cuenta de **administrador** en `/ingresar` y abrí `/admin`. Si entrás sin sesión, la tienda te pide ingresar; si entrás con una cuenta de cliente, no ves el panel.

### 2. Qué hay en cada pestaña

| Pestaña | Para qué sirve |
| --- | --- |
| **Estadísticas** | Pedidos totales, ventas, pedidos de hoy y productos más vendidos; gráfico por fechas |
| **Órdenes** | Todos los pedidos con el **email del comprador**, fecha, items, total y estado; filtros, paginación y **exportar a CSV** |
| **Productos** | Crear, editar y borrar productos, con variantes, stock, precio y **imagen** (jpg, png, gif o webp, hasta 5 MB) |
| **Categorías** | Crear, editar y borrar categorías |
| **Devoluciones** | Aprobar o rechazar solicitudes y **procesar el reembolso** de las aprobadas |
| **Usuarios** | Cambiar el rol (cliente o administrador) y activar o desactivar cuentas |
| **Cupones** | Crear cupones con código, descuento, mínimo de compra, vigencia y usos |
| **Anuncios** | Configurar el anuncio de la tienda |
| **Envío y regalo** | Monto de envío gratis, producto de regalo y **zonas de envío** (código postal, costo y plazo) |
| **Preguntas frecuentes** | Editar las preguntas y respuestas; Salchi Bot responde lo mismo |

### 3. Cambiar el estado de un pedido
En **Órdenes**, usá el desplegable **Cambiar estado** de cada fila. El sistema solo ofrece los cambios válidos:

```
PAGO_PENDIENTE → COMPLETADA → ENVIADA
       └───── cancelar ──┴──→ CANCELADA
```

- **Enviada** y **Cancelada** son finales: no admiten más cambios.
- **Cancelar** un pedido libera el stock y, si ya estaba pagado, devuelve el dinero.
- Si algo intenta saltarse esas reglas, el sistema responde con un error 409 y no cambia nada.

### 4. Cosas que conviene saber
- Si dos administradores editan lo mismo a la vez, el segundo recibe un aviso de que el dato cambió y tiene que recargar.
- Las imágenes subidas en la versión publicada pueden perderse cuando el servidor se reinicia.
- Los emails de confirmación son simulados (quedan como archivos en la carpeta `outbox/`).

---

## Problemas frecuentes

| Síntoma | Qué hacer |
| --- | --- |
| La tienda tarda mucho en abrir | Esperá hasta un minuto y recargá: el servidor estaba dormido |
| «Stock insuficiente» al pagar | Alguien compró antes; bajá la cantidad o elegí otro producto |
| El formulario no deja finalizar | Mirá los mensajes en rojo debajo de cada campo |
| No aparece MercadoPago | Todavía no está habilitado en ese entorno; usá la tarjeta de prueba |
| Volvés de MercadoPago y no ves la confirmación | Tocá **Volver al sitio** y revisá **Mis pedidos** |
| Un cupón «no es válido» | Puede estar vencido, agotado o requerir una compra mínima |
