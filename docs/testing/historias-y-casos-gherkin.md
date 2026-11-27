# Historias de usuario y especificación de casos (Gherkin)

Cada historia tiene al menos 3 escenarios y al menos 40 % de casos negativos o de borde (objetivo 8.2 de la declaración de IA). Cada escenario es un caso de prueba con ID `CP-<HU>-<n>`; la columna «Dónde se automatiza» de la [matriz de trazabilidad](matriz-de-trazabilidad.md) dice qué prueba lo ejecuta.

> **Aviso sobre los IDs.** Las HU de este documento se derivaron de la aplicación y de su documentación, y los IDs (`HU-01` a `HU-24`) son provisorios: si la lista de la entrega de diseño del 03/11 o Jira usan otra numeración, se reemplaza el ID en este archivo y en la matriz, sin tocar el contenido de los escenarios.

Rol «invitado»: quien compra sin cuenta. «Cliente»: con sesión. «Administrador»: rol `ADMIN`.

## Cliente e invitado

### HU-01 Explorar el catálogo
*Como visitante quiero filtrar por categoría y buscar por nombre para encontrar rápido lo que necesito.*

```gherkin
Característica: Explorar el catálogo

  Escenario: CP-01-1 Filtrar por categoría
    Dado que estoy en la lista de productos
    Cuando elijo la categoría "Alimentos"
    Entonces solo veo productos de esa categoría

  Escenario: CP-01-2 Buscar por nombre
    Dado que estoy en la lista de productos
    Cuando escribo "pelota" en el buscador
    Entonces la lista muestra solo productos cuyo nombre o marca coincide

  Escenario: CP-01-3 Búsqueda sin resultados (negativo)
    Cuando busco un texto que no coincide con ningún producto
    Entonces veo un mensaje de que no hay resultados y la tienda sigue funcionando

  Escenario: CP-01-4 Dirección de un producto que no existe (negativo)
    Cuando abro la dirección de un producto inexistente
    Entonces la tienda lo informa sin romperse
```

### HU-02 Ver el detalle de un producto
*Como cliente quiero ver variantes, stock y reseñas para decidir la compra.*

```gherkin
Característica: Detalle de producto

  Escenario: CP-02-1 Ver variantes y precio
    Dado un producto con variantes
    Cuando abro su detalle
    Entonces puedo elegir una variante y veo su precio y su stock

  Escenario: CP-02-2 Publicar una reseña con sesión
    Dado que inicié sesión
    Cuando publico una reseña con calificación y texto
    Entonces la reseña aparece en el producto

  Escenario: CP-02-3 Reseña sin sesión (negativo)
    Dado que no inicié sesión
    Cuando intento publicar una reseña
    Entonces la API responde 401 y no se guarda nada

  Escenario: CP-02-4 Segunda reseña del mismo producto (negativo)
    Dado que ya reseñé un producto
    Cuando intento reseñarlo otra vez
    Entonces la API responde 409 «Ya dejaste una reseña para este producto»

  Escenario: CP-02-5 Calificación fuera de rango (negativo)
    Cuando publico una reseña con calificación 0 o 6
    Entonces la API la rechaza (solo se admite de 1 a 5)
```

### HU-03 Armar el carrito
*Como cliente quiero agregar, cambiar cantidades y quitar productos para controlar mi compra.*

```gherkin
Característica: Carrito

  Escenario: CP-03-1 Agregar y sumar cantidad
    Cuando agrego un producto y toco «Sumar cantidad»
    Entonces el subtotal es el precio por la cantidad

  Escenario: CP-03-2 Quitar un producto
    Dado un carrito con productos
    Cuando toco «Quitar» en uno
    Entonces desaparece y el total se recalcula

  Escenario: CP-03-3 Carrito vacío (borde)
    Cuando no tengo productos
    Entonces veo «Tu carrito está vacío» y un botón para ver productos

  Escenario: CP-03-4 Superar el stock (negativo)
    Dado un producto con 3 unidades
    Cuando pido 4
    Entonces la compra se rechaza con 409 «Stock insuficiente»
```

### HU-04 Calcular el envío por código postal
*Como comprador quiero saber cuánto cuesta y cuándo llega mi pedido.*

```gherkin
Característica: Envío por código postal

  Escenario: CP-04-1 Código postal de una zona con tarifa
    Cuando escribo "1425"
    Entonces veo la zona, el costo y el plazo («llega en …»)

  Escenario: CP-04-2 Código postal con letras
    Cuando escribo "C1425ABC"
    Entonces el envío se calcula igual

  Escenario: CP-04-3 Código postal inválido (negativo)
    Cuando escribo "14x5" o "12"
    Entonces veo «Revisá el código postal: son 4 números» y no puedo finalizar

  Escenario: CP-04-4 Zona sin tarifa propia (borde)
    Cuando escribo un código postal que ninguna zona cubre
    Entonces veo «Envío al resto del país» con la tarifa general
```

### HU-05 Aplicar un cupón
*Como comprador quiero usar un código de descuento.*

```gherkin
Característica: Cupones

  Escenario: CP-05-1 Cupón válido
    Dado un cupón activo y una compra que supera el mínimo
    Cuando lo aplico
    Entonces el descuento se resta del total

  Escenario: CP-05-2 Cupón vencido, agotado o inexistente (negativo)
    Cuando aplico un cupón que no corresponde
    Entonces veo «El cupón no es válido para esta compra» y el total no cambia

  Escenario: CP-05-3 Cupón con caracteres inválidos (negativo)
    Cuando escribo "DESC 10%"
    Entonces el formulario lo rechaza sin consultar al servidor y la API responde 422

  Escenario: CP-05-4 El subtotal baja del mínimo (borde)
    Dado un cupón aplicado
    Cuando quito productos y el subtotal queda bajo el mínimo
    Entonces el cupón se quita del pedido y se avisa
```

### HU-06 Envío gratis y regalo
*Como cliente quiero que el pedido tenga envío gratis y un regalo si supera los montos que fijó la tienda.*

```gherkin
Característica: Promociones del carrito

  Escenario: CP-06-1 Alcanzar el monto de envío gratis
    Dado un umbral de envío gratis configurado
    Cuando el subtotal lo alcanza
    Entonces el envío figura «Gratis»

  Escenario: CP-06-2 Alcanzar el monto del regalo
    Cuando el subtotal supera el mínimo del regalo
    Entonces el regalo se suma al pedido sin costo

  Escenario: CP-06-3 Regalo agotado (borde)
    Cuando el regalo se queda sin stock durante la compra
    Entonces la venta se completa igual, sin regalo

  Escenario: CP-06-4 Promociones apagadas (negativo)
    Dado que el administrador las apagó
    Entonces el carrito no las muestra ni las aplica
```

### HU-07 Ruleta del día
*Como cliente quiero girar la ruleta diaria para obtener un descuento.*

```gherkin
Característica: Ruleta diaria

  Escenario: CP-07-1 Girar la ruleta
    Dado que no giré hoy
    Cuando giro
    Entonces se sortea una categoría con 10 % de descuento

  Escenario: CP-07-2 Descuento solo en la categoría sorteada
    Dado un carrito con productos de varias categorías
    Entonces el descuento se calcula solo sobre los de la categoría sorteada

  Escenario: CP-07-3 Girar dos veces el mismo día (negativo)
    Dado que ya giré hoy
    Cuando intento girar de nuevo
    Entonces no se me otorga otro descuento

  Escenario: CP-07-4 Descuento y cupón juntos (borde)
    Entonces el total nunca queda por debajo de cero
```

### HU-08 Completar los datos de envío
*Como comprador quiero ingresar mis datos de envío con ayuda si me equivoco.*

```gherkin
Característica: Datos de envío validados

  Escenario: CP-08-1 Datos correctos
    Cuando completo nombre, dirección, ciudad y código postal válidos
    Entonces puedo finalizar la compra

  Escenario: CP-08-2 Nombre con números o símbolos (negativo)
    Cuando escribo "Juan123" en nombre y salgo del campo
    Entonces veo el error debajo del campo y no se envía el formulario

  Escenario: CP-08-3 Teléfono mal formado (negativo)
    Cuando escribo "abc" en teléfono
    Entonces veo «El teléfono debe tener entre 6 y 15 números»; vacío es válido porque es opcional

  Escenario: CP-08-4 La API repite las reglas (negativo)
    Cuando se llama a la API sin pasar por el formulario con un nombre "Juan123"
    Entonces responde 422 «Datos de envío inválidos» antes de tocar el stock

  Escenario: CP-08-5 Varios errores a la vez (borde)
    Cuando envío con varios campos inválidos
    Entonces se muestran todos los errores y el foco va al primero
```

### HU-09 Pagar con tarjeta
*Como comprador quiero pagar y saber si el pago fue aprobado o rechazado.*

```gherkin
Característica: Pago con tarjeta (pasarela)

  Escenario: CP-09-1 Pago aprobado
    Dado un carrito con productos y datos válidos
    Cuando pago con la tarjeta de prueba aprobada
    Entonces veo «Compra confirmada», el stock baja y el carrito se vacía

  Escenario: CP-09-2 Pago rechazado (negativo)
    Cuando pago con la tarjeta de prueba rechazada
    Entonces no se cobra, se avisa y el carrito se conserva

  Escenario: CP-09-3 Pasarela caída o lenta (negativo)
    Cuando la pasarela no responde en 5 segundos
    Entonces la venta queda «pago pendiente» y se cancela o confirma después, liberando el stock si falla

  Escenario: CP-09-4 Reintento con la misma clave (borde)
    Cuando se repite la compra con el mismo `Idempotency-Key`
    Entonces no se crea un segundo pedido ni se cobra dos veces

  Escenario: CP-09-5 Compradores simultáneos de la última unidad (borde)
    Dado un producto con 1 unidad
    Cuando 12 personas compran a la vez
    Entonces solo una compra se concreta (sin sobreventa)
```

### HU-10 Pagar con MercadoPago
*Como comprador quiero pagar con MercadoPago y volver a la tienda.*

```gherkin
Característica: MercadoPago

  Escenario: CP-10-1 Pago aprobado
    Dado que MercadoPago está habilitado
    Cuando elijo MercadoPago y pago con la tarjeta de prueba aprobada (titular APRO)
    Entonces el pedido queda completado y veo «Compra confirmada»

  Escenario: CP-10-2 Pago rechazado (negativo)
    Cuando pago con el titular FUND (fondos insuficientes)
    Entonces el pedido no figura como pagado

  Escenario: CP-10-3 MercadoPago sin configurar (negativo)
    Dado que no hay token
    Entonces la opción no se ofrece y la API responde 422 si se la fuerza

  Escenario: CP-10-4 Aviso (webhook) falso (borde)
    Cuando llega un aviso de un pago que no existe o de otro monto
    Entonces el pedido no se confirma
```

### HU-11 Registrarse e iniciar sesión
*Como usuario quiero crear una cuenta y entrar de forma segura.*

```gherkin
Característica: Ingreso

  Escenario: CP-11-1 Ingreso correcto
    Cuando ingreso con un email y contraseña válidos
    Entonces inicio sesión y vuelvo a donde quería ir

  Escenario: CP-11-2 Contraseña incorrecta (negativo)
    Cuando ingreso con una contraseña equivocada
    Entonces veo el error y no inicio sesión

  Escenario: CP-11-3 Demasiados intentos (negativo)
    Cuando fallo 5 veces seguidas con el mismo email
    Entonces la API responde 429 y bloquea temporalmente

  Escenario: CP-11-4 Token vencido (borde)
    Dado un token vencido
    Cuando hago una operación protegida
    Entonces la sesión se renueva sola, o se pide ingresar de nuevo

  Escenario: CP-11-5 Acceso sin sesión (negativo)
    Cuando abro «Mis pedidos» o el carrito sin sesión para comprar
    Entonces me lleva a «Ingresar»
```

### HU-12 Ingresar con Google
*Como usuario quiero entrar con mi cuenta de Google.*

```gherkin
Característica: Ingreso con Google

  Escenario: CP-12-1 Token de Google válido
    Cuando Google devuelve un token válido
    Entonces entro y, si no existía, se crea mi cuenta

  Escenario: CP-12-2 Token falso o de otra aplicación (negativo)
    Cuando se envía un token mal firmado o con otro destinatario
    Entonces la API lo rechaza con 401

  Escenario: CP-12-3 Cuenta de Google sin contraseña propia (borde)
    Entonces no se ofrece cambiar la contraseña
```

### HU-13 Ver mis pedidos
*Como cliente quiero ver el estado de mis compras.*

```gherkin
Característica: Mis pedidos

  Escenario: CP-13-1 Ver mis pedidos
    Dado que compré
    Cuando entro a «Mis pedidos»
    Entonces veo mi compra con su estado, total y productos

  Escenario: CP-13-2 Solo veo los míos (negativo)
    Cuando consulto los pedidos
    Entonces nunca aparecen los de otro usuario y no aparecen datos internos de pago

  Escenario: CP-13-3 Sin pedidos (borde)
    Dado que no compré
    Entonces veo un mensaje de que no hay pedidos
```

### HU-14 Solicitar una devolución
*Como cliente quiero pedir la devolución de un producto que compré.*

```gherkin
Característica: Devoluciones

  Escenario: CP-14-1 Devolución válida
    Dado un producto comprado
    Cuando solicito devolverlo con un motivo
    Entonces la solicitud queda pendiente

  Escenario: CP-14-2 Producto no comprado (negativo)
    Cuando pido devolver un producto que nunca compré
    Entonces la API lo rechaza

  Escenario: CP-14-3 Más unidades que las compradas (negativo)
    Cuando pido devolver más unidades de las compradas o ya devueltas
    Entonces la API lo rechaza

  Escenario: CP-14-4 Regalo (borde)
    Cuando intento devolver el producto de regalo
    Entonces no se permite
```

### HU-15 Guardar favoritos
*Como cliente quiero guardar productos para verlos después.*

```gherkin
Característica: Favoritos

  Escenario: CP-15-1 Agregar y quitar un favorito
    Cuando marco y desmarco un producto
    Entonces aparece y desaparece de «Favoritos»

  Escenario: CP-15-2 Sin sesión (negativo)
    Dado que no inicié sesión
    Entonces la API de favoritos responde 401

  Escenario: CP-15-3 Lista vacía (borde)
    Entonces veo un mensaje de que no tengo favoritos
```

### HU-16 Preguntas frecuentes y Salchi Bot
*Como cliente quiero respuestas rápidas y recomendaciones.*

```gherkin
Característica: Preguntas frecuentes y Salchi Bot

  Escenario: CP-16-1 Las preguntas frecuentes se despliegan
    Cuando abro una pregunta
    Entonces veo su respuesta

  Escenario: CP-16-2 Salchi Bot responde lo mismo que las preguntas frecuentes
    Cuando le hago la misma pregunta al bot
    Entonces su respuesta coincide

  Escenario: CP-16-3 Recomendación por mascota y presupuesto
    Cuando escribo «algo para mi perro, hasta $20.000»
    Entonces recomienda hasta 3 productos del catálogo dentro de ese presupuesto

  Escenario: CP-16-4 Mensaje que no entiende (negativo)
    Cuando escribo algo sin sentido
    Entonces responde que no entendió y ofrece sugerencias, sin error

  Escenario: CP-16-5 Si el servidor falla (negativo)
    Entonces el bot se disculpa y la tienda sigue funcionando
```

### HU-17 Gestionar mi cuenta
*Como cliente quiero cambiar mi nombre y mi contraseña.*

```gherkin
Característica: Mi cuenta

  Escenario: CP-17-1 Cambiar la contraseña
    Cuando ingreso la actual y una nueva válida
    Entonces la contraseña cambia

  Escenario: CP-17-2 Contraseña actual incorrecta (negativo)
    Entonces no se cambia y se informa el error

  Escenario: CP-17-3 Nombre con HTML (negativo)
    Cuando guardo un nombre con <script>
    Entonces la API responde 400
```

## Administrador

### HU-18 Acceso restringido al panel
*Como dueño quiero que solo el administrador entre al panel.*

```gherkin
Característica: Panel de administración

  Escenario: CP-18-1 El administrador entra
    Cuando ingreso con una cuenta ADMIN y abro /admin
    Entonces veo las pestañas Estadísticas, Órdenes, Productos y las demás

  Escenario: CP-18-2 Un cliente no entra (negativo)
    Cuando un cliente abre /admin
    Entonces no ve el panel y la API responde 403

  Escenario: CP-18-3 Sin sesión (negativo)
    Cuando abro /admin sin sesión
    Entonces me pide ingresar y la API responde 401

  Escenario: CP-18-4 Token inválido o vencido en una ruta de administrador (borde)
    Entonces la API responde 401 (no 403) para que la tienda renueve la sesión
```

### HU-19 Gestionar productos y categorías
*Como administrador quiero crear, editar y borrar productos y categorías, con imágenes.*

```gherkin
Característica: Productos y categorías

  Escenario: CP-19-1 Crear un producto
    Cuando completo nombre, precio, categoría y stock válidos
    Entonces el producto aparece en el catálogo

  Escenario: CP-19-2 Datos inválidos (negativo)
    Cuando envío un producto sin nombre, con precio 0 o negativo, o con stock negativo
    Entonces la API responde 400 con la causa

  Escenario: CP-19-3 Subir una imagen (borde)
    Cuando subo un .png de hasta 5 MB
    Entonces queda disponible; un .exe o un archivo mayor a 5 MB se rechaza con 400

  Escenario: CP-19-4 Edición simultánea (borde)
    Cuando dos administradores editan el mismo producto
    Entonces la actualización con `If-Match` desactualizado responde 412
```

### HU-20 Gestionar pedidos y su estado
*Como administrador quiero cambiar el estado de un pedido solo a estados válidos.*

```gherkin
Característica: Estados de la venta (patrón State)

  Escenario: CP-20-1 Transiciones permitidas
    Dado un pedido en PAGO_PENDIENTE, COMPLETADA o ENVIADA
    Cuando pasa a COMPLETADA, ENVIADA o se cancela según la regla
    Entonces el cambio se aplica (4 combinaciones válidas)

  Escenario: CP-20-2 Transición inválida (negativo)
    Dado un pedido CANCELADA o ENVIADA
    Cuando intento cambiar su estado
    Entonces la API responde 409 (8 combinaciones inválidas)

  Escenario: CP-20-3 Cancelar libera el stock y reembolsa (borde)
    Dado un pedido pagado
    Cuando lo cancelo
    Entonces se libera el stock y se devuelve el dinero

  Escenario: CP-20-4 El panel muestra el comprador (borde)
    Entonces la tabla de pedidos muestra el email del comprador, no su identificador interno
```

### HU-21 Configurar la tienda
*Como administrador quiero gestionar cupones, anuncios, envío gratis y regalo, zonas de envío y preguntas frecuentes.*

```gherkin
Característica: Configuración de la tienda

  Escenario: CP-21-1 Crear un cupón
    Cuando creo un cupón con código y descuento
    Entonces los clientes pueden usarlo

  Escenario: CP-21-2 Cupón duplicado o inválido (negativo)
    Entonces la API lo rechaza con el motivo

  Escenario: CP-21-3 Anuncio, envío gratis, regalo y zonas
    Cuando los cambio
    Entonces la tienda y el carrito muestran los valores nuevos

  Escenario: CP-21-4 Un cliente no puede cambiarlos (negativo)
    Entonces la API responde 403
```

### HU-22 Aprobar devoluciones y reembolsar
```gherkin
Característica: Devoluciones del administrador

  Escenario: CP-22-1 Aprobar y reembolsar
    Dado una devolución pendiente
    Cuando la apruebo y proceso el reembolso
    Entonces el dinero se devuelve y la devolución se cierra

  Escenario: CP-22-2 Rechazar una devolución
    Entonces queda rechazada y no cuenta para el límite de unidades

  Escenario: CP-22-3 Reembolsar una devolución no aprobada (negativo)
    Entonces la API lo rechaza

  Escenario: CP-22-4 Un cliente intenta aprobar (negativo)
    Entonces la API responde 403
```

### HU-23 Gestionar usuarios
```gherkin
Característica: Usuarios

  Escenario: CP-23-1 Cambiar el rol de un usuario
    Entonces el cambio se aplica

  Escenario: CP-23-2 Desactivar un usuario
    Entonces no puede iniciar sesión

  Escenario: CP-23-3 Un cliente intenta gestionar usuarios (negativo)
    Entonces la API responde 403

  Escenario: CP-23-4 Un administrador no puede quitarse su propio rol ni desactivarse (negativo)
    Entonces la API lo rechaza para que el sistema nunca quede sin administrador

  Escenario: CP-23-5 Cambiar el rol o desactivar cierra las sesiones de esa persona (borde)
    Entonces sus tokens dejan de valer
```

### HU-24 Reprocesar mensajes fallidos
*Como administrador quiero reprocesar los eventos que no se pudieron entregar.*

```gherkin
Característica: Cola de mensajes muertos (DLQ)

  Escenario: CP-24-1 Un evento fallido va a la DLQ
    Cuando un evento falla tres veces
    Entonces queda en la DLQ

  Escenario: CP-24-2 Reprocesar desde el panel
    Cuando el administrador lo reprocesa
    Entonces se entrega

  Escenario: CP-24-3 Mensaje duplicado (borde)
    Entonces el consumidor idempotente no lo procesa dos veces

  Escenario: CP-24-4 Broker caído (negativo)
    Entonces el evento se guarda en la base (outbox) y se reintenta después
```

## Resumen
24 historias, 98 escenarios. Los casos negativos y de borde son más del 40 % en todas las historias.
