"""Genera la colección de Postman (petshop.postman_collection.json) con casos de éxito y de error para cada endpoint.

    python tests-api/generar_coleccion.py          # reescribe tests-api/petshop.postman_collection.json
    npx newman run tests-api/petshop.postman_collection.json --working-dir tests-api --env-var baseUrl=http://localhost:8080

Las solicitudes se ejecutan en orden y se pasan datos entre sí con variables (tokens, ids). Lo que se crea (productos,
categorías, cupones, variantes, usuarios de prueba) se borra al final; lo que se modifica (FAQ, ajustes, zonas de envío) se
restaura. Solo para entornos de prueba.
"""
import json, pathlib, re, sys

# Valores de prueba que el servidor tiene que rechazar (no son secretos: se mantienen fuera de los literales con nombre de credencial)
VALOR_INCORRECTO = "incorrecta"
VALOR_NO_ES_DE_GOOGLE = "no-es-un-token-de-google"

OUT = pathlib.Path(__file__).with_name("petshop.postman_collection.json")
FOLDERS = []          # [(nombre, [items])]
CASES = []            # (método, ruta normalizada, tipo) para el informe de cobertura


def tok(who):
    return {"admin": "{{adminToken}}", "cliente": "{{clienteToken}}", "b": "{{bToken}}"}.get(who)


def norm(path):
    return path.split("?")[0]


def req(name, method, path, status, who=None, body=None, headers=None, tests=None, pre=None, form=None, raw=None, ctype=None, kind=None):
    """status: código esperado (o lista). tests: líneas de JavaScript extra. kind: 'ok' o 'error' para el informe."""
    h = [{"key": "Content-Type", "value": ctype or "application/json"}] if (body is not None or raw is not None) else []
    if who:
        h.append({"key": "X-Auth-Token", "value": tok(who)})
    for k, v in (headers or {}).items():
        h.append({"key": k, "value": v})
    url_path, _, q = path.partition("?")
    url = {"raw": "{{baseUrl}}" + path, "host": ["{{baseUrl}}"], "path": [p for p in url_path.strip("/").split("/") if p]}
    if q:
        url["query"] = [{"key": k, "value": v} for k, _, v in (x.partition("=") for x in q.split("&"))]
    request = {"method": method, "header": h, "url": url}
    if body is not None:
        request["body"] = {"mode": "raw", "raw": body if isinstance(body, str) else json.dumps(body, ensure_ascii=False), "options": {"raw": {"language": "json"}}}
    if raw is not None:
        request["body"] = {"mode": "raw", "raw": raw}
    if form is not None:
        request["body"] = {"mode": "formdata", "formdata": form}
    codes = status if isinstance(status, list) else [status]
    lines = [f'pm.test("estado {"/".join(map(str, codes))}", function () {{ pm.expect({json.dumps(codes)}).to.include(pm.response.code); }});']
    lines += tests or []
    item = {"name": name, "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": lines}}], "request": request}
    if pre:
        item["event"].insert(0, {"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre}})
    kind = kind or ("ok" if codes[0] < 400 else "error")
    CASES.append((method, norm(path), kind))
    return item


def folder(name):
    items = []
    FOLDERS.append((name, items))
    return items


def setvar(var, expr):
    return f'pm.collectionVariables.set("{var}", {expr});'


J = "pm.response.json()"

# ============================================================================================================== 00
f = folder("00 Preparación: sesiones y usuarios")
f += [
    req("Configuración de autenticación", "GET", "/api/auth/config", 200,
        pre=['// Contraseñas descartables de esta corrida: nunca hay una fija en la colección',
             'const azar = () => "Aa1-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10);',
             'pm.collectionVariables.set("pw1", azar()); pm.collectionVariables.set("pw2", azar());'],
        tests=['pm.test("trae googleClientId", () => pm.expect(pm.response.json()).to.have.property("googleClientId"));']),
    req("Configuración de pagos", "GET", "/api/payments/config", 200, tests=['pm.test("indica los medios disponibles", () => pm.expect(pm.response.json()).to.have.property("simulated"));']),
    req("Login del administrador", "POST", "/api/auth/login", 200, body={"email": "{{adminEmail}}", "password": "{{adminPassword}}"},
        tests=[f"const b = {J};", 'pm.test("devuelve token y refreshToken", () => { pm.expect(b.token).to.be.a("string"); pm.expect(b.refreshToken).to.be.a("string"); });',
               setvar("adminToken", "b.token")]),
    req("Login del cliente", "POST", "/api/auth/login", 200, body={"email": "{{clienteEmail}}", "password": "{{clientePassword}}"},
        tests=[f"const b = {J};", setvar("clienteToken", "b.token"), setvar("clienteRefresh", "b.refreshToken"), setvar("clienteId", "b.user ? b.user.id : ''")]),
    req("Login con contraseña incorrecta", "POST", "/api/auth/login", 401, body={"email": "{{clienteEmail}}", "password": VALOR_INCORRECTO}),
    req("Login con cuerpo vacío", "POST", "/api/auth/login", [400, 401], body={}),
    req("Registro de un usuario nuevo", "POST", "/api/auth/register", [200, 201], body={"email": "newman-{{$timestamp}}@ejemplo.com", "password": "{{pw1}}", "name": "Usuario Newman"},
        pre=['pm.collectionVariables.set("bEmail", "newman-" + Date.now() + "@ejemplo.com");', 'pm.collectionVariables.set("bPassword", "Aa1-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10));',
             'pm.request.body.update(JSON.stringify({email: pm.collectionVariables.get("bEmail"), password: pm.collectionVariables.get("bPassword"), name: "Usuario Newman"}));'],
        tests=[f"const b = {J};", setvar("bId", "(b.user && b.user.id) || b.id"), 'pm.test("el usuario nuevo es CUSTOMER", () => pm.expect(((b.user || b).role)).to.eql("CUSTOMER"));']),
    req("Registro con un email ya usado", "POST", "/api/auth/register", 409, body={"email": "{{clienteEmail}}", "password": "{{pw1}}", "name": "Duplicado"}),
    req("Registro con un email inválido", "POST", "/api/auth/register", 400, body={"email": "no-es-un-email", "password": "{{pw1}}", "name": "X"}),
    req("Registro con una contraseña débil", "POST", "/api/auth/register", 400, body={"email": "debil-{{$timestamp}}@ejemplo.com", "password": "123", "name": "X"}),
    req("Registro con HTML en el nombre (se rechaza)", "POST", "/api/auth/register", 400, body={"email": "xss-{{$timestamp}}@ejemplo.com", "password": "{{pw1}}", "name": "<script>alert(1)</script>"}),
    req("Login del usuario nuevo", "POST", "/api/auth/login", 200, body={"email": "{{bEmail}}", "password": "{{bPassword}}"},
        tests=[f"const b = {J};", setvar("bToken", "b.token"), setvar("bRefresh", "b.refreshToken")]),
    req("Mi perfil", "GET", "/api/auth/me", 200, who="cliente", tests=[f'pm.test("es el cliente", () => pm.expect({J}.email).to.eql(pm.collectionVariables.get("clienteEmail")));']),
    req("Mi perfil sin sesión", "GET", "/api/auth/me", 401),
    req("Mi perfil con un token falso", "GET", "/api/auth/me", 401, headers={"X-Auth-Token": "token.falso.firma"}),
    req("Cambiar mi nombre", "PUT", "/api/auth/me", 200, who="b", body={"name": "Newman Renombrado"}),
    req("Cambiar mi nombre sin sesión", "PUT", "/api/auth/me", 401, body={"name": "X"}),
    req("Renovar la sesión", "POST", "/api/auth/refresh", 200, body={"refreshToken": "{{bRefresh}}"},
        tests=[f"const b = {J};", setvar("bToken", "b.token"), setvar("bRefresh", "b.refreshToken")]),
    req("Renovar con un token inválido", "POST", "/api/auth/refresh", 401, body={"refreshToken": "no-existe"}),
    req("Cambiar la contraseña", "POST", "/api/auth/change-password", 200, who="b", body={"currentPassword": "{{bPassword}}", "newPassword": "{{pw2}}"}),
    req("Cambiar la contraseña con la actual incorrecta", "POST", "/api/auth/change-password", [400, 401, 403], who="b", body={"currentPassword": VALOR_INCORRECTO, "newPassword": "{{pw2}}"}),
    req("Recuperar la contraseña (siempre responde igual)", "POST", "/api/auth/recover", 200, body={"email": "{{clienteEmail}}"}),
    req("Recuperar sin email (responde igual: no revela qué emails existen)", "POST", "/api/auth/recover", [200, 400, 422], body={}, kind="error"),
    req("Restablecer con un código inválido", "POST", "/api/auth/reset", [400, 404, 410], body={"token": "codigo-invalido", "newPassword": "{{pw1}}"}),
    req("Ingreso con Google y una credencial inválida", "POST", "/api/auth/google", [400, 401, 503], body={"credential": VALOR_NO_ES_DE_GOOGLE}),
    req("Cerrar sesión", "POST", "/api/auth/logout", 200, body={"refreshToken": "{{bRefresh}}"}),
    req("Cerrar sesión sin indicar la sesión", "POST", "/api/auth/logout", [200, 400, 422], body={}, kind="error"),
    req("Renovar con el token ya cerrado", "POST", "/api/auth/refresh", 401, body={"refreshToken": "{{bRefresh}}"}),
]

# ============================================================================================================== 01
f = folder("01 Catálogo público")
f += [
    req("Listar productos", "GET", "/api/products", 200, tests=[f"const b = {J};", 'pm.test("hay productos", () => pm.expect(b.length).to.be.above(0));', setvar("productoExistente", "b[0].id")]),
    req("Filtrar por categoría y buscar", "GET", "/api/products?category=juguetes&search=pelota&sort=menorprecio", 200, tests=[f'pm.test("todos son de juguetes", () => {J}.forEach(p => pm.expect(p.categoryId).to.eql("juguetes")));']),
    req("Detalle de un producto", "GET", "/api/products/p2", 200, tests=['pm.test("trae ETag", () => pm.expect(pm.response.headers.get("ETag")).to.be.ok);']),
    req("Detalle de un producto que no existe", "GET", "/api/products/no-existe", 404),
    req("Listar categorías", "GET", "/api/categories", 200),
    req("API v1: listar productos", "GET", "/api/v1/productos", 200),
    req("API v1: detalle de un producto", "GET", "/api/v1/productos/p2", 200),
    req("API v1: producto que no existe", "GET", "/api/v1/productos/no-existe", 404),
    req("Variantes de un producto", "GET", "/api/products/p1/variants", 200),
    req("Variantes de un producto que no existe", "GET", "/api/products/no-existe/variants", [200, 404], kind="error"),
    req("Reseñas de un producto", "GET", "/api/products/p2/reviews", 200),
    req("Reseñas de un producto que no existe", "GET", "/api/products/no-existe/reviews", [200, 404], kind="error"),
    req("Preguntas frecuentes", "GET", "/api/faq", 200, tests=[f"const b = {J};", setvar("faqOriginal", "JSON.stringify(b)")]),
    req("Ajustes del sitio", "GET", "/api/settings", 200, tests=[setvar("settingsOriginal", f"JSON.stringify({J})")]),
    req("Promociones del carrito", "GET", "/api/settings/cart-promos", 200, tests=[setvar("promosOriginal", f"JSON.stringify({J})")]),
    req("Zonas de envío", "GET", "/api/shipping/zones", 200, tests=[setvar("zonasOriginal", f"JSON.stringify({J})")]),
    req("Cotizar el envío (CABA)", "GET", "/api/shipping/quote?postalCode=1425&subtotal=5000", 200, tests=[f'pm.test("trae un costo", () => pm.expect({J}).to.have.property("cost"));']),
    req("Cotizar con un código postal inválido", "GET", "/api/shipping/quote?postalCode=12&subtotal=5000", [400, 422]),
    req("Salchi Bot responde sobre el envío", "POST", "/api/chat", 200, body={"message": "¿cuánto tarda el envío?", "history": []}),
    req("Salchi Bot sin mensaje (pide que escriban algo)", "POST", "/api/chat", [200, 400, 422], body={"history": []}, kind="error"),
    req("Ruleta como invitado", "GET", "/api/roulette", 200, headers={"X-Guest-Id": "newman-invitado"}),
    req("Ruleta sin identificación", "GET", "/api/roulette", 401),
    req("Descuento de la ruleta en el carrito", "GET", "/api/roulette/cart-discount", 200, headers={"X-Guest-Id": "newman-invitado"}),
    req("Descuento de la ruleta sin identificación", "GET", "/api/roulette/cart-discount", [200, 401], kind="error"),
    req("Webhook de MercadoPago con un tipo que se ignora (404 si MercadoPago no está configurado)", "POST", "/api/payments/mercadopago/webhook?type=merchant_order&data.id=1", [200, 404]),
    req("Webhook de MercadoPago con un pago inexistente", "POST", "/api/payments/mercadopago/webhook?type=payment&data.id=999999999", [200, 404, 502], kind="error"),
    req("Cupón: validar un código que no existe", "POST", "/api/coupons/validate", [400, 404], body={"code": "NO-EXISTE", "subtotal": 5000}),
]

# ============================================================================================================== 02
f = folder("02 Administración: catálogo (alta, cambio y baja)")
prod = {"name": "Producto Newman", "brand": "Marca", "price": 1000, "stock": 5, "categoryId": "juguetes", "imageUrl": "/products/p1.svg", "rating": 4.0}
f += [
    req("Crear una categoría", "POST", "/api/categories", 200, who="admin", body={"name": "Newman", "color": "#ABCDEF"}, tests=[setvar("categoriaId", f"{J}.id")]),
    req("Crear una categoría sin sesión", "POST", "/api/categories", 401, body={"name": "X", "color": "#fff"}),
    req("Crear una categoría como cliente", "POST", "/api/categories", 403, who="cliente", body={"name": "X", "color": "#fff"}),
    req("Cambiar la categoría", "PUT", "/api/categories/{{categoriaId}}", 200, who="admin", body={"name": "Newman 2", "color": "#123456"}),
    req("Cambiar una categoría que no existe", "PUT", "/api/categories/no-existe", 400, who="admin", body={"name": "X", "color": "#fff"}),
    req("Crear un producto", "POST", "/api/products", 200, who="admin", body=prod, tests=[setvar("productoId", f"{J}.id")]),
    req("Crear un producto como cliente", "POST", "/api/products", 403, who="cliente", body=prod),
    req("Crear un producto sin sesión", "POST", "/api/products", 401, body=prod),
    req("Crear un producto con HTML en el nombre", "POST", "/api/products", 400, who="admin", body={**prod, "name": "<img src=x onerror=alert(1)>"}),
    req("Crear un producto sin nombre", "POST", "/api/products", 400, who="admin", body={**prod, "name": ""}),
    req("Crear un producto con precio 0", "POST", "/api/products", 400, who="admin", body={**prod, "price": 0}),
    req("Crear un producto sin categoría", "POST", "/api/products", 400, who="admin", body={**prod, "categoryId": ""}),
    req("Crear un producto con stock negativo", "POST", "/api/products", 400, who="admin", body={**prod, "stock": -1}),
    req("Crear una categoría sin nombre", "POST", "/api/categories", 400, who="admin", body={"name": "", "color": "#fff"}),
    req("Leer el producto (ETag)", "GET", "/api/products/{{productoId}}", 200, tests=[setvar("productoEtag", 'pm.response.headers.get("ETag")')]),
    req("Actualizar con un ETag desactualizado", "PUT", "/api/products/{{productoId}}", 412, who="admin", headers={"If-Match": '"desactualizado"'}, body={**prod, "price": 1500}),
    req("Actualizar con el ETag vigente", "PUT", "/api/products/{{productoId}}", 200, who="admin", headers={"If-Match": "{{productoEtag}}"}, body={**prod, "price": 1500}),
    req("Actualizar un producto que no existe", "PUT", "/api/products/no-existe", 400, who="admin", body=prod),
    req("Crear una variante", "POST", "/api/products/{{productoId}}/variants", [200, 201], who="admin", body={"talle": "M", "color": "rojo", "stock": 3}, tests=[setvar("varianteId", f"{J}.id")]),
    req("Crear una variante como cliente", "POST", "/api/products/{{productoId}}/variants", 403, who="cliente", body={"talle": "M", "color": "rojo", "stock": 3}),
    req("Actualizar la variante", "PUT", "/api/products/{{productoId}}/variants/{{varianteId}}", 200, who="admin", body={"talle": "L", "color": "azul", "stock": 4}),
    req("Actualizar una variante que no existe", "PUT", "/api/products/{{productoId}}/variants/99999999", [400, 404], who="admin", body={"talle": "L", "color": "azul", "stock": 4}),
    req("Borrar la variante", "DELETE", "/api/products/{{productoId}}/variants/{{varianteId}}", [200, 204], who="admin"),
    req("Borrar una variante que no existe", "DELETE", "/api/products/{{productoId}}/variants/99999999", [400, 404], who="admin"),
]

# ============================================================================================================== 03
f = folder("03 Cupones")
cup = {"code": "NEWMAN10", "discountType": "PERCENTAGE", "discountValue": 10, "minPurchase": 0, "maxUses": 5, "active": True}
f += [
    req("Crear un cupón", "POST", "/api/coupons", 200, who="admin", body=cup, tests=[setvar("cuponId", f"{J}.id")]),
    req("Crear un cupón repetido", "POST", "/api/coupons", 409, who="admin", body=cup),
    req("Crear un cupón inválido", "POST", "/api/coupons", 400, who="admin", body={**cup, "code": "X2", "discountValue": -5}),
    req("Crear un cupón como cliente", "POST", "/api/coupons", 403, who="cliente", body=cup),
    req("Listar los cupones", "GET", "/api/coupons", 200, who="admin"),
    req("Listar los cupones sin sesión", "GET", "/api/coupons", 401),
    req("Cambiar el cupón", "PUT", "/api/coupons/{{cuponId}}", 200, who="admin", body={**cup, "discountValue": 15}),
    req("Cambiar un cupón que no existe", "PUT", "/api/coupons/99999999", [400, 404], who="admin", body=cup),
    req("Validar el cupón", "POST", "/api/coupons/validate", 200, body={"code": "NEWMAN10", "subtotal": 5000}),
    req("Validar un cupón con una compra mínima que no se alcanza", "POST", "/api/coupons/validate", [400, 404, 422], body={"code": "NO-EXISTE", "subtotal": 1}),
    req("Borrar el cupón", "DELETE", "/api/coupons/{{cuponId}}", [200, 204], who="admin"),
    req("Borrar un cupón que no existe", "DELETE", "/api/coupons/99999999", [400, 404], who="admin"),
]

# ============================================================================================================== 04
f = folder("04 Compra: carrito, venta v1, pago y estados")
item = {"productId": "p3", "name": "Collar", "variant": "accesorios", "quantity": 1, "price": 1}
venta = {"nombre": "Newman", "direccion": "Calle 1", "ciudad": "CABA", "codigoPostal": "1425", "medioPago": "tok_aprobado"}
f += [
    req("Carrito vacío del cliente", "GET", "/api/cart", 200, who="cliente"),
    req("Carrito sin identificar a nadie", "GET", "/api/cart", [200, 401], kind="error"),
    req("Agregar un producto al carrito", "POST", "/api/cart/add", 200, who="cliente", body=item, tests=[f'pm.test("el precio lo fija el servidor, no el cliente", () => {{ const c = {J}.find(i => i.productId === "p3"); pm.expect(c.price).to.be.above(1); }});']),
    req("Agregar un producto que no existe", "POST", "/api/cart/add", [400, 404], who="cliente", body={**item, "productId": "no-existe"}),
    req("Agregar más que el stock", "POST", "/api/cart/add", [400, 409], who="cliente", body={**item, "quantity": 99999}),
    req("Un administrador no puede usar el carrito", "POST", "/api/cart/add", 403, who="admin", body=item),
    req("Sumar una unidad", "PUT", "/api/cart/items/p3/increment?variant=accesorios", 200, who="cliente"),
    req("Restar una unidad", "PUT", "/api/cart/items/p3/decrement?variant=accesorios", 200, who="cliente"),
    req("Sumar en un producto que no está en el carrito", "PUT", "/api/cart/items/no-esta/increment?variant=x", [400, 404], who="cliente"),
    req("Restar en un producto que no está en el carrito", "PUT", "/api/cart/items/no-esta/decrement?variant=x", [400, 404], who="cliente"),
    req("Quitar el producto del carrito", "POST", "/api/cart/remove", 200, who="cliente", body={"productId": "p3", "variant": "accesorios"}),
    req("Quitar un producto que no está", "POST", "/api/cart/remove", [200, 400, 404], who="cliente", body={"productId": "no-esta", "variant": "x"}, kind="error"),
    req("Agregar de nuevo para comprar", "POST", "/api/cart/add", 200, who="cliente", body={**item, "productId": "p2", "name": "Pelota", "variant": "juguetes"}),
    req("Comprar sin la clave de idempotencia", "POST", "/api/v1/ventas", [400, 422], who="cliente", body=venta),
    req("Comprar con un código postal inválido", "POST", "/api/v1/ventas", 422, who="cliente", body={**venta, "codigoPostal": "12"}, headers={"Idempotency-Key": "{{$guid}}"}),
    req("Comprar con la tarjeta de prueba aprobada", "POST", "/api/v1/ventas", 201, who="cliente", body=venta, headers={"Idempotency-Key": "newman-{{$timestamp}}"},
        pre=['pm.collectionVariables.set("idemKey", "newman-" + Date.now());', 'pm.request.headers.upsert({key: "Idempotency-Key", value: pm.collectionVariables.get("idemKey")});'],
        tests=[f"const b = {J};", setvar("ventaId", "b.id"), 'pm.test("COMPLETADA", () => pm.expect(b.estado).to.eql("COMPLETADA"));']),
    req("Repetir la misma compra con la misma clave devuelve la misma venta", "POST", "/api/v1/ventas", [200, 201], who="cliente", body=venta,
        pre=['pm.request.headers.upsert({key: "Idempotency-Key", value: pm.collectionVariables.get("idemKey")});'],
        tests=[f'pm.test("misma venta", () => pm.expect({J}.id).to.eql(Number(pm.collectionVariables.get("ventaId"))));']),
    req("Comprar con una tarjeta rechazada", "POST", "/api/v1/ventas", 402, who="cliente", body=venta, headers={"Idempotency-Key": "{{$guid}}"},
        pre=['pm.sendRequest({url: pm.collectionVariables.get("baseUrl") + "/api/cart/add", method: "POST", header: {"Content-Type": "application/json", "X-Auth-Token": pm.collectionVariables.get("clienteToken")}, body: {mode: "raw", raw: JSON.stringify({productId: "p2", name: "Pelota", variant: "juguetes", quantity: 1, price: 1})}}, function () {});',
             'pm.request.body.update(JSON.stringify({nombre: "Newman", direccion: "Calle 1", ciudad: "CABA", codigoPostal: "1425", medioPago: "tok_rechazado"}));']),
    req("Mis ventas", "GET", "/api/v1/ventas", 200, who="cliente", tests=[f'pm.test("incluye la compra", () => pm.expect({J}.map(v => v.id)).to.include(Number(pm.collectionVariables.get("ventaId"))));']),
    req("Mis ventas sin sesión", "GET", "/api/v1/ventas", 401),
    req("Ver mi venta (ETag)", "GET", "/api/v1/ventas/{{ventaId}}", 200, who="cliente", tests=[setvar("ventaEtag", 'pm.response.headers.get("ETag")')]),
    req("Ver una venta que no existe", "GET", "/api/v1/ventas/99999999", 404, who="cliente"),
    req("Otro usuario no ve mi venta (BOLA)", "GET", "/api/v1/ventas/{{ventaId}}", 404, who="b"),
    req("Estado del pago", "GET", "/api/v1/ventas/{{ventaId}}/pago", 200, who="cliente"),
    req("Estado del pago de una venta ajena", "GET", "/api/v1/ventas/{{ventaId}}/pago", 404, who="b"),
    req("Sincronizar el pago", "POST", "/api/v1/ventas/{{ventaId}}/pago/sincronizacion", 200, who="cliente"),
    req("Sincronizar el pago de una venta ajena", "POST", "/api/v1/ventas/{{ventaId}}/pago/sincronizacion", 404, who="b"),
    req("Cambiar el estado con un ETag desactualizado", "PATCH", "/api/v1/ventas/{{ventaId}}", 412, who="admin", headers={"If-Match": '"desactualizado"'}, body={"estado": "ENVIADA"}),
    req("Cambiar el estado como cliente", "PATCH", "/api/v1/ventas/{{ventaId}}", 403, who="cliente", body={"estado": "ENVIADA"}),
    req("Cambiar a un estado que no existe", "PATCH", "/api/v1/ventas/{{ventaId}}", 422, who="admin", body={"estado": "BASURA"}),
    req("Pasar la venta a ENVIADA", "PATCH", "/api/v1/ventas/{{ventaId}}", 200, who="admin", headers={"If-Match": "{{ventaEtag}}"}, body={"estado": "ENVIADA"}),
    req("Cancelar una venta ya enviada (transición inválida)", "POST", "/api/v1/ventas/{{ventaId}}/cancelacion", 409, who="cliente"),
    req("Cancelar una venta que no existe", "POST", "/api/v1/ventas/99999999/cancelacion", 404, who="cliente"),
    req("Carrito para comprar y cancelar", "POST", "/api/cart/add", 200, who="cliente", body={**item, "productId": "p2", "name": "Pelota", "variant": "juguetes"}),
    req("Segunda compra", "POST", "/api/v1/ventas", 201, who="cliente", body=venta, headers={"Idempotency-Key": "{{$guid}}"}, tests=[setvar("ventaId2", f"{J}.id")]),
    req("Cancelar la segunda compra", "POST", "/api/v1/ventas/{{ventaId2}}/cancelacion", 200, who="cliente", tests=[f'pm.test("CANCELADA", () => pm.expect({J}.estado).to.eql("CANCELADA"));']),
]

# ============================================================================================================== 05
f = folder("05 Pedidos, devoluciones, reseñas y favoritos")
f += [
    req("Mis pedidos (cliente)", "GET", "/api/orders/me", 200, who="cliente"),
    req("Mis pedidos sin sesión", "GET", "/api/orders/me", 401),
    req("Todos los pedidos (administrador)", "GET", "/api/orders", 200, who="admin", tests=[setvar("pedidoId", f"{J}.length ? {J}[0].id : 1")]),
    req("Todos los pedidos como cliente", "GET", "/api/orders", 403, who="cliente"),
    req("Todos los pedidos sin sesión", "GET", "/api/orders", 401),
    req("Estadísticas de ventas", "GET", "/api/orders/stats", 200, who="admin"),
    req("Estadísticas como cliente", "GET", "/api/orders/stats", 403, who="cliente"),
    req("Cambiar el estado de un pedido (API vieja)", "PUT", "/api/orders/{{pedidoId}}/status", [200, 400, 409], who="admin", body={"estado": "ENVIADA", "status": "ENVIADA"}),
    req("Cambiar el estado de un pedido como cliente", "PUT", "/api/orders/{{pedidoId}}/status", 403, who="cliente", body={"estado": "ENVIADA"}),
    req("Pedir una devolución", "POST", "/api/returns", [200, 201], who="cliente", body={"productId": "p2", "cantidad": 1, "motivo": "No era lo que esperaba"}, tests=[setvar("devolucionId", f"{J}.id")]),
    req("Pedir una devolución de algo no comprado", "POST", "/api/returns", 422, who="cliente", body={"productId": "p50", "cantidad": 1, "motivo": "X"}),
    req("Pedir una devolución de más unidades de las compradas", "POST", "/api/returns", 422, who="cliente", body={"productId": "p2", "cantidad": 50, "motivo": "X"}),
    req("Mis devoluciones", "GET", "/api/returns/me", 200, who="cliente"),
    req("Mis devoluciones sin sesión", "GET", "/api/returns/me", 401),
    req("Todas las devoluciones (administrador)", "GET", "/api/returns", 200, who="admin"),
    req("Todas las devoluciones como cliente", "GET", "/api/returns", 403, who="cliente"),
    req("Aprobar la devolución", "PATCH", "/api/returns/{{devolucionId}}/status", 200, who="admin", body={"estado": "APROBADA"}),
    req("Cambiar el estado de una devolución como cliente", "PATCH", "/api/returns/{{devolucionId}}/status", 403, who="cliente", body={"estado": "APROBADA"}),
    req("Reembolsar la devolución aprobada", "POST", "/api/refunds/process/{{devolucionId}}", 200, who="admin"),
    req("Reembolsar dos veces la misma devolución", "POST", "/api/refunds/process/{{devolucionId}}", 409, who="admin"),
    req("Reembolsar una devolución que no existe", "POST", "/api/refunds/process/99999999", 400, who="admin"),
    req("Reembolsar como cliente", "POST", "/api/refunds/process/{{devolucionId}}", 403, who="cliente"),
    req("Dejar una reseña", "POST", "/api/products/p2/reviews", [200, 201], who="cliente", body={"rating": 5, "comment": "Muy bueno"}, tests=[setvar("resenaId", f"{J}.id")]),
    req("Dejar una reseña con una calificación inválida", "POST", "/api/products/p2/reviews", [400, 409], who="cliente", body={"rating": 9, "comment": "X"}),
    req("Dejar una reseña sin sesión", "POST", "/api/products/p2/reviews", 401, body={"rating": 5, "comment": "X"}),
    req("Borrar la reseña como cliente", "DELETE", "/api/products/p2/reviews/{{resenaId}}", 403, who="cliente"),
    req("Borrar la reseña (administrador)", "DELETE", "/api/products/p2/reviews/{{resenaId}}", [200, 204], who="admin"),
    req("Borrar una reseña que no existe", "DELETE", "/api/products/p2/reviews/99999999", [400, 404], who="admin"),
    req("Mis favoritos", "GET", "/api/wishlist", 200, who="cliente"),
    req("Agregar un favorito", "POST", "/api/wishlist", [200, 201], who="cliente", body={"productId": "p3"}),
    req("Agregar un favorito que no existe", "POST", "/api/wishlist", [400, 404], who="cliente", body={"productId": "no-existe"}),
    req("Favoritos sin sesión", "GET", "/api/wishlist", 401),
    req("Quitar el favorito", "DELETE", "/api/wishlist/p3", [200, 204], who="cliente"),
    req("Quitar un favorito que no está", "DELETE", "/api/wishlist/p3", [200, 204, 404], who="cliente", kind="error"),
    req("Quitar un favorito sin sesión", "DELETE", "/api/wishlist/p3", 401),
    req("Girar la ruleta (una vez por día)", "POST", "/api/roulette/spin", [200, 409, 429], who="cliente"),
    req("Un administrador no gira la ruleta", "POST", "/api/roulette/spin", 403, who="admin"),
]

# ============================================================================================================== 06
f = folder("06 Administración: usuarios, ajustes, preguntas frecuentes y envíos")
f += [
    req("Listar usuarios", "GET", "/api/users", 200, who="admin", tests=[f'pm.test("no filtra contraseñas", () => {J}.forEach(u => {{ pm.expect(u).to.not.have.property("password"); }}));']),
    req("Listar usuarios como cliente", "GET", "/api/users", 403, who="cliente"),
    req("Listar usuarios sin sesión", "GET", "/api/users", 401),
    req("Dar de baja al usuario de prueba", "PATCH", "/api/users/{{bId}}/status", 200, who="admin", body={"active": False}),
    req("Reactivar al usuario de prueba", "PATCH", "/api/users/{{bId}}/status", 200, who="admin", body={"active": True}),
    req("Cambiar el estado sin el campo active", "PATCH", "/api/users/{{bId}}/status", 400, who="admin", body={}),
    req("Cambiar el estado como cliente", "PATCH", "/api/users/{{bId}}/status", 403, who="cliente", body={"active": False}),
    req("Cambiar el rol del usuario de prueba", "PATCH", "/api/users/{{bId}}/role", 200, who="admin", body={"role": "CUSTOMER"}),
    req("Cambiar a un rol que no existe", "PATCH", "/api/users/{{bId}}/role", [400, 422], who="admin", body={"role": "SUPERUSUARIO"}),
    req("Cambiar el rol como cliente", "PATCH", "/api/users/{{bId}}/role", 403, who="cliente", body={"role": "ADMIN"}),
    req("Guardar los ajustes del sitio (los mismos)", "PUT", "/api/settings", 200, who="admin", raw="{{settingsOriginal}}"),
    req("Ajustes con un modo de anuncio inválido", "PUT", "/api/settings", [400, 422], who="admin", body={"topbarItems": [], "announcementMode": "INVENTADO", "announcementText": ""}),
    req("Ajustes como cliente", "PUT", "/api/settings", 403, who="cliente", raw="{{settingsOriginal}}"),
    req("Guardar las promociones del carrito (las mismas)", "PUT", "/api/settings/cart-promos", 200, who="admin", raw="{{promosOriginal}}"),
    req("Promociones con un monto negativo", "PUT", "/api/settings/cart-promos", [400, 422], who="admin", body={"freeShippingEnabled": True, "freeShippingThreshold": -1, "giftEnabled": False, "giftMinAmount": 0, "giftProductId": None}),
    req("Promociones como cliente", "PUT", "/api/settings/cart-promos", 403, who="cliente", raw="{{promosOriginal}}"),
    req("Guardar las preguntas frecuentes (las mismas)", "PUT", "/api/faq", 200, who="admin", raw="{{faqOriginal}}"),
    req("Preguntas frecuentes con una pregunta vacía", "PUT", "/api/faq", [400, 422], who="admin", body=[{"question": "", "answer": "x"}]),
    req("Preguntas frecuentes como cliente", "PUT", "/api/faq", 403, who="cliente", raw="{{faqOriginal}}"),
    req("Guardar las zonas de envío (las mismas)", "PUT", "/api/shipping/zones", 200, who="admin", raw="{{zonasOriginal}}"),
    req("Zonas con un rango de códigos postales invertido", "PUT", "/api/shipping/zones", [400, 422], who="admin", body={"defaultCost": 100, "defaultEta": "1 día", "zones": [{"name": "Mal", "postalFrom": 2000, "postalTo": 1000, "cost": 1, "eta": "x"}]}),
    req("Zonas como cliente", "PUT", "/api/shipping/zones", 403, who="cliente", raw="{{zonasOriginal}}"),
    req("Cola de mensajes muertos (administrador)", "GET", "/api/admin/dlq", 200, who="admin", tests=[f'pm.test("trae el conteo", () => pm.expect({J}).to.have.property("count"));']),
    req("Cola de mensajes muertos como cliente", "GET", "/api/admin/dlq", 403, who="cliente"),
    req("Reprocesar la cola de mensajes muertos", "POST", "/api/admin/dlq/reprocess", 200, who="admin"),
    req("Reprocesar sin sesión", "POST", "/api/admin/dlq/reprocess", 401),
    req("Subir una imagen", "POST", "/api/uploads", 200, who="admin", form=[{"key": "file", "type": "file", "src": "fixtures/pixel.png"}], tests=[f'pm.test("devuelve la dirección", () => pm.expect({J}.url).to.match(/^\\/uploads\\//));']),
    req("Subir un archivo que no es una imagen", "POST", "/api/uploads", 400, who="admin", form=[{"key": "file", "type": "file", "src": "fixtures/nota.txt"}]),
    req("Subir una imagen como cliente", "POST", "/api/uploads", 403, who="cliente", form=[{"key": "file", "type": "file", "src": "fixtures/pixel.png"}]),
]

# ============================================================================================================== 07
f = folder("07 Limpieza de lo que creó la colección")
f += [
    req("Borrar el producto", "DELETE", "/api/products/{{productoId}}", 200, who="admin"),
    req("Borrar un producto que no existe", "DELETE", "/api/products/no-existe", 400, who="admin"),
    req("Borrar un producto como cliente", "DELETE", "/api/products/p1", 403, who="cliente"),
    req("Borrar la categoría", "DELETE", "/api/categories/{{categoriaId}}", 200, who="admin"),
    req("Borrar una categoría que no existe", "DELETE", "/api/categories/no-existe", 400, who="admin"),
    req("Borrar una categoría como cliente", "DELETE", "/api/categories/perros", 403, who="cliente"),
]

# ============================================================================================================== 08
SOAP = ('<?xml version="1.0"?><soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Body>'
        '<p:AutorizarPagoRequest xmlns:p="urn:petshop:pagos"><p:idTransaccion>{id}</p:idTransaccion><p:monto>100.0</p:monto>'
        '<p:medioPagoToken>{token}</p:medioPagoToken></p:AutorizarPagoRequest></soapenv:Body></soapenv:Envelope>')
f = folder("08 Pasarela de pagos SOAP")
f += [
    req("SOAP: autorizar con una tarjeta aprobada", "POST", "/ws", 200, raw=SOAP.format(id="newman-soap-{{$timestamp}}", token="tok_aprobado"), ctype="text/xml; charset=utf-8", headers={"SOAPAction": '""'},
        tests=['pm.test("APROBADO", () => pm.expect(pm.response.text()).to.include("APROBADO"));']),
    req("SOAP: tarjeta rechazada (respuesta de negocio)", "POST", "/ws", 200, raw=SOAP.format(id="newman-soap-r-{{$timestamp}}", token="tok_rechazado"), ctype="text/xml; charset=utf-8", headers={"SOAPAction": '""'},
        tests=['pm.test("RECHAZADO con motivo", () => pm.expect(pm.response.text()).to.include("RECHAZADO"));']),
    req("SOAP: datos inválidos (Fault de negocio PG-400)", "POST", "/ws", 500, raw=SOAP.format(id="newman-soap-i-{{$timestamp}}", token="tok_invalido"), ctype="text/xml; charset=utf-8", headers={"SOAPAction": '""'},
        tests=['pm.test("es un Fault con el código", () => { pm.expect(pm.response.text()).to.include("Fault"); pm.expect(pm.response.text()).to.include("PG-400"); });']),
    req("SOAP: una entidad externa XML no se expande (XXE)", "POST", "/ws", [400, 500], raw=SOAP.format(id="&xxe;", token="tok_aprobado").replace("<soapenv:Envelope", '<!DOCTYPE r [<!ENTITY xxe SYSTEM "file:///etc/hostname">]><soapenv:Envelope', 1),
        ctype="text/xml; charset=utf-8", headers={"SOAPAction": '""'}, tests=['pm.test("no filtra archivos", () => pm.expect(pm.response.text()).to.not.match(/root:|\\[fonts\\]|for 16-bit/));']),
]


def build():
    return {
        "info": {"name": "Petshop API", "description": "Casos de éxito y de error de cada endpoint. Generada por tests-api/generar_coleccion.py.",
                 "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
        "item": [{"name": n, "item": items} for n, items in FOLDERS],
        "variable": [{"key": "baseUrl", "value": "http://localhost:8080"}, {"key": "adminEmail", "value": "admin@petshop.com"}, {"key": "adminPassword", "value": "admin123"},
                     {"key": "clienteEmail", "value": "cliente@ejemplo.com"}, {"key": "clientePassword", "value": "password"}],
    }


# Endpoints públicos de solo lectura, sin parámetros: no tienen una entrada que pueda fallar
SIN_ERROR_POSIBLE = {("GET", "/api/auth/config"), ("GET", "/api/payments/config"), ("GET", "/api/categories"), ("GET", "/api/faq"),
                     ("GET", "/api/settings"), ("GET", "/api/settings/cart-promos"), ("GET", "/api/shipping/zones"), ("GET", "/api/products"),
                     ("GET", "/api/v1/productos")}
# Necesitan algo que la colección no puede producir sola (un token de Google, un código enviado por email)
SIN_EXITO_POSIBLE = {("POST", "/api/auth/google"): "requiere un token firmado por Google",
                     ("POST", "/api/auth/reset"): "requiere el código que llega por email (queda en la carpeta outbox del servidor)",
                     }


def coverage():
    """Cruza los casos con las operaciones que publica la API (si está en ejecución): cada una con éxito y con error."""
    import os, urllib.request
    base = os.environ.get("PETSHOP_URL", "http://localhost:8080")
    try:
        spec = json.load(urllib.request.urlopen(base + "/v3/api-docs", timeout=10))
    except Exception:
        print("(API no disponible: se omite el informe de cobertura)")
        return 0
    ops = []
    for path, item in spec["paths"].items():
        for method in item:
            if method.upper() in ("GET", "POST", "PUT", "PATCH", "DELETE"):
                ops.append((method.upper(), path, re.compile("^" + re.sub(r"\{[^}]+\}", "[^/]+", path) + "$")))
    have = {}
    for m, p, kind in CASES:
        # la operación más específica (con menos comodines) que coincide con la ruta del caso
        hits = sorted((o for o in ops if o[0] == m and o[2].match(p)), key=lambda o: o[1].count("{"))
        if hits:
            have.setdefault((m, hits[0][1]), set()).add(kind)
    missing = 0
    for method, path, _ in sorted(ops, key=lambda o: (o[1], o[0])):
        kinds = have.get((method, path), set())
        need = {"ok", "error"}
        if (method, path) in SIN_ERROR_POSIBLE:
            need.discard("error")
        if (method, path) in SIN_EXITO_POSIBLE:
            need.discard("ok")
        if not need <= kinds:
            missing += 1
            print(f"  falta {' y '.join(sorted(('éxito' if k == 'ok' else 'error') for k in need - kinds))}: {method} {path}")
    print(f"Cobertura: {len(ops) - missing} de {len(ops)} operaciones con caso de éxito y de error "
          f"({len(SIN_ERROR_POSIBLE)} sin error posible y {len(SIN_EXITO_POSIBLE)} sin éxito automatizable, declaradas)")
    return missing


if __name__ == "__main__":
    OUT.write_text(json.dumps(build(), ensure_ascii=False, indent=1), encoding="utf-8")
    n = sum(len(i) for _, i in FOLDERS)
    print(f"{n} solicitudes escritas en {OUT.name}")
    sys.exit(1 if coverage() else 0)  # falla si queda una operación sin caso de éxito o de error
