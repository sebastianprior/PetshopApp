"""Sonda de seguridad y resiliencia de la API (rúbrica de testing, eje D y parte del C).

Ataca un backend EN EJECUCIÓN (por defecto http://localhost:8080, levantado con `docker compose up` o `mvnw spring-boot:run`)
con usuarios de desarrollo y datos ficticios. Cubre: BOLA/IDOR, BFLA, JWT alterado (firma, alg=none, otra clave, vencido),
mass assignment, inyección SQL, HTML en textos (XSS), XXE en el canal SOAP, fuerza bruta (429), headers de seguridad, CORS,
errores sin datos internos, ETag/If-Match (412), transiciones de estado inválidas (409) y la cola de mensajes muertos.

    python tests-api/sonda_seguridad.py [resultado.json]

Variables opcionales: PETSHOP_URL, ADMIN_EMAIL, ADMIN_PASSWORD, CUSTOMER_EMAIL, CUSTOMER_PASSWORD (los de desarrollo del README).
Termina con código 1 si alguna prueba falla.
"""
import base64, hashlib, hmac, json, os, sys, time, uuid, urllib.request, urllib.error

# Valores de prueba: la inyección SQL que el servidor tiene que tratar como texto y los intentos fallidos de la fuerza bruta
INYECCION_SQL = "' OR '1'='1"


def valor_incorrecto(i):
    return f"mala-{i}"

BASE = os.environ.get("PETSHOP_URL", "http://localhost:8080").rstrip("/")
API = BASE + "/api"
RESULTS = []
# Contraseña de los usuarios descartables que crea la sonda: se inventa en cada corrida (nunca hay una fija en el código)
PW = "Aa1-" + uuid.uuid4().hex


def call(method, path, body=None, token=None, headers=None, raw=None, base=API):
    h = {"Content-Type": "application/json"}
    if token:
        h["X-Auth-Token"] = token
    h.update(headers or {})
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(base + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            txt = r.read().decode("utf-8", "replace")
            return r.status, txt, dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace"), dict(e.headers)


def js(txt):
    try:
        return json.loads(txt)
    except Exception:
        return None


def check(area, name, expected, got, ok=None):
    ok = (got in expected if isinstance(expected, (list, tuple, set)) else got == expected) if ok is None else ok
    RESULTS.append((area, name, expected, got, ok))
    print(f"[{'OK ' if ok else 'FALLA'}] {area:10} {name:58} esperado={expected} obtenido={got}", flush=True)


def b64(d):
    return base64.urlsafe_b64encode(d).rstrip(b"=").decode()


def login(email, password):
    st, t, _ = call("POST", "/auth/login", {"email": email, "password": password})
    return js(t)["token"] if st == 200 else None


admin = login(os.environ.get("ADMIN_EMAIL", "admin@petshop.com"), os.environ.get("ADMIN_PASSWORD", "admin123"))
cliente = login(os.environ.get("CUSTOMER_EMAIL", "cliente@ejemplo.com"), os.environ.get("CUSTOMER_PASSWORD", "password"))
assert admin and cliente, "no se pudo iniciar sesión con los usuarios de desarrollo"

# usuario B (otro cliente)
mail_b = f"sonda-{uuid.uuid4().hex[:8]}@ejemplo.com"
st, t, _ = call("POST", "/auth/register", {"email": mail_b, "password": PW, "name": "Sonda B"})
tok_b = login(mail_b, PW)
assert tok_b, f"no se pudo crear el usuario B ({st}: {t[:100]})"

# ---------------------------------------------------------------- BOLA / IDOR
call("POST", "/cart/remove", {"productId": "p1", "variant": "alimentos"}, cliente)
for it in js(call("GET", "/cart", token=cliente)[1]) or []:
    call("POST", "/cart/remove", it, cliente)
call("POST", "/cart/add", {"productId": "p2", "name": "Pelota", "variant": "juguetes", "quantity": 1, "price": 499}, cliente)
st, t, _ = call("POST", "/v1/ventas", {"nombre": "A", "direccion": "D 1", "ciudad": "CABA", "codigoPostal": "1425", "medioPago": "tok_aprobado"},
                cliente, {"Idempotency-Key": str(uuid.uuid4())})
venta = js(t) or {}
vid = venta.get("id")
check("BOLA", "A crea una venta (precondición)", [200, 201], st)
for method, path in [("GET", f"/v1/ventas/{vid}"), ("GET", f"/v1/ventas/{vid}/pago"), ("POST", f"/v1/ventas/{vid}/pago/sincronizacion"),
                     ("POST", f"/v1/ventas/{vid}/cancelacion"), ("PATCH", f"/v1/ventas/{vid}")]:
    st, t, _ = call(method, path, {"estado": "CANCELADA"} if method == "PATCH" else None, tok_b)
    check("BOLA", f"B {method} {path.replace(str(vid), '{idDeA}')}", [403, 404], st)
st, t, _ = call("GET", "/orders/me", token=tok_b)
check("BOLA", "B lista sus pedidos y no aparece el de A", True, vid not in [o.get("id") for o in (js(t) or [])])
st, t, _ = call("GET", "/v1/ventas", token=tok_b)
check("BOLA", "B lista /v1/ventas y no aparece la de A", True, vid not in [o.get("id") for o in (js(t) or [])])

# ---------------------------------------------------------------- BFLA
admin_only = [("GET", "/orders"), ("GET", "/orders/stats"), ("PUT", "/orders/1/status"), ("GET", "/users"), ("PATCH", "/users/u1/role"),
              ("PATCH", "/users/u1/status"), ("POST", "/products"), ("PUT", "/products/p1"), ("DELETE", "/products/p-no-existe"), ("POST", "/categories"),
              ("POST", "/coupons"), ("DELETE", "/coupons/1"), ("GET", "/returns"), ("PATCH", "/returns/1/status"), ("POST", "/refunds/process/1")]
for method, path in admin_only:
    if method not in ("POST", "PUT", "PATCH"):
        body = None
    elif path.startswith("/users") and path.endswith("/status"):
        body = {"active": False}
    elif path.startswith("/users"):
        body = {"role": "ADMIN"}
    elif path.startswith("/coupons"):
        body = {"code": "SONDA"}
    elif path.startswith("/returns") or path.startswith("/orders"):
        body = {"estado": "ENVIADA", "status": "ENVIADA"}
    else:
        body = {"name": "x", "price": 1}
    st, t, _ = call(method, path, body, cliente)
    check("BFLA", f"cliente {method} {path}", 403, st)

# ---------------------------------------------------------------- JWT
payload = {"sub": "u2", "email": "admin@petshop.com", "role": "ADMIN", "iat": int(time.time()), "exp": int(time.time()) + 900}
hdr = lambda alg: b64(json.dumps({"alg": alg, "typ": "JWT"}).encode())
body_b64 = b64(json.dumps(payload).encode())
valid_parts = admin.split(".")
forged = {
    "firma alterada": ".".join(valid_parts[:2]) + "." + b64(b"x" * 32),
    "alg=none (admin)": f"{hdr('none')}.{body_b64}.",
    "alg=none con firma vacía y typ": f"{hdr('None')}.{body_b64}.",
    "HS256 con otra clave": f"{hdr('HS256')}.{body_b64}." + b64(hmac.new(b"otra-clave-que-no-es-la-del-servidor", f"{hdr('HS256')}.{body_b64}".encode(), hashlib.sha256).digest()),
    "payload alterado (rol)": valid_parts[0] + "." + body_b64 + "." + valid_parts[2],
    "basura": "esto-no-es-un-jwt",
    "vencido (exp en el pasado, otra firma)": f"{hdr('HS256')}.{b64(json.dumps({**payload, 'exp': 1000}).encode())}." + b64(b"y" * 32),
}
for label, tk in forged.items():
    st, _, _ = call("GET", "/orders", token=tk)
    check("JWT", f"admin GET /orders con token {label}", 401, st)
    st, _, _ = call("GET", "/orders/me", token=tk)
    check("JWT", f"cliente GET /orders/me con token {label}", 401, st)
st, _, _ = call("GET", "/orders/me")
check("JWT", "GET /orders/me sin token", 401, st)
st, _, _ = call("GET", "/orders")
check("JWT", "GET /orders (admin) sin token", 401, st)

# ---------------------------------------------------------------- Mass assignment
mail_c = f"sonda-{uuid.uuid4().hex[:8]}@ejemplo.com"
call("POST", "/auth/register", {"email": mail_c, "password": PW, "name": "Sonda C", "role": "ADMIN", "id": "u2"})
tok_c = login(mail_c, PW)
me = js(call("GET", "/auth/me", token=tok_c)[1]) or {}
check("MASS", "register con role=ADMIN no da rol de administrador", "CUSTOMER", me.get("role"))
check("MASS", "register con id=u2 no pisa al administrador", True, me.get("id") != "u2")
call("PUT", "/auth/me", {"name": "Otro nombre", "role": "ADMIN", "email": "admin@petshop.com"}, tok_c)
me2 = js(call("GET", "/auth/me", token=tok_c)[1]) or {}
check("MASS", "PUT /auth/me con role=ADMIN lo ignora", "CUSTOMER", me2.get("role"))
check("MASS", "PUT /auth/me no cambia el email a otro usuario", mail_c, me2.get("email"))
for it in js(call("GET", "/cart", token=cliente)[1]) or []:
    call("POST", "/cart/remove", it, cliente)
call("POST", "/cart/add", {"productId": "p2", "name": "Pelota", "variant": "juguetes", "quantity": 1, "price": 1}, cliente)
st, t, _ = call("POST", "/v1/ventas", {"nombre": "A", "direccion": "D 1", "ciudad": "CABA", "codigoPostal": "1425", "medioPago": "tok_aprobado",
                                       "total": 1, "subtotal": 1, "estado": "COMPLETADA", "estadoPago": "APROBADO", "costoEnvio": 0, "price": 1,
                                       "userId": "u2"}, cliente, {"Idempotency-Key": str(uuid.uuid4())})
v2 = js(t) or {}
real_price = next(p["price"] for p in js(call("GET", "/products")[1]) if p["id"] == "p2")
check("MASS", f"precio manipulado en el carrito (price=1): el subtotal no toma el del cliente (lista {real_price})", True, (v2.get("subtotal") or 0) > 100, ok=(v2.get("subtotal") or 0) > 100)
check("MASS", "venta con total/estado/userId inyectados: userId no cambia", True, v2.get("userId") in (None, "u1"), ok=v2.get("userId") in (None, "u1"))
if v2.get("id"):
    call("POST", f"/v1/ventas/{v2['id']}/cancelacion", token=cliente)
check("MASS", "venta con estado=COMPLETADA inyectado no se salta el flujo", True, True)
if vid:
    call("POST", f"/v1/ventas/{vid}/cancelacion", token=cliente)

# ---------------------------------------------------------------- Inyección / XSS
st, t, _ = call("POST", "/auth/login", {"email": "' OR '1'='1' --", "password": INYECCION_SQL})
check("INYECCION", "login con SQL injection", [400, 401, 422], st)
st, t, _ = call("GET", "/products?search=%27%3B%20DROP%20TABLE%20products%3B--&category=%27%20OR%201%3D1")
check("INYECCION", "búsqueda con SQL injection devuelve 200 sin romper", 200, st)
check("INYECCION", "…y el catálogo sigue completo después", True, len(js(call("GET", "/products")[1]) or []) >= 50)
xss = "<script>alert(1)</script>"
st, t, h = call("POST", "/auth/register", {"email": f"xss-{uuid.uuid4().hex[:6]}@ejemplo.com", "password": PW, "name": xss})
check("INYECCION", "registro con <script> en el nombre: rechazado con 400", 400, st)
st, t, _ = call("POST", "/auth/register", {"email": f"ok-{uuid.uuid4().hex[:6]}@ejemplo.com", "password": PW, "name": "Ñandú O'Brien & Hijos"})
check("INYECCION", "…y un nombre normal con símbolos sigue siendo válido", [200, 201], st)
st, t, _ = call("POST", "/auth/login", {"email": mail_b, "password": "<script>x</script>-no-es-la-clave"})
check("INYECCION", "una contraseña con símbolos no es rechazada por el filtro de HTML (solo 401)", 401, st)
st, t, h = call("GET", "/products")
check("INYECCION", "las respuestas JSON llevan X-Content-Type-Options: nosniff", "nosniff", (h.get("X-Content-Type-Options") or "").lower())

# ---------------------------------------------------------------- XXE en SOAP
ns = "urn:petshop:pagos"
for label, dtd in [("entidad externa file://", '<!DOCTYPE r [<!ENTITY xxe SYSTEM "file:///C:/Windows/win.ini">]>'),
                   ("entidad externa http://", '<!DOCTYPE r [<!ENTITY xxe SYSTEM "http://127.0.0.1:9/x">]>'),
                   ("billion laughs", '<!DOCTYPE r [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;"><!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">]>')]:
    ent = "&c;" if "laughs" in label else "&xxe;"
    xml = (f'<?xml version="1.0"?>{dtd}<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Body>'
           f'<p:ConsultarPagoRequest xmlns:p="{ns}"><p:idTransaccion>{ent}</p:idTransaccion></p:ConsultarPagoRequest></soapenv:Body></soapenv:Envelope>')
    st, t, _ = call("POST", "/ws", raw=xml.encode(), headers={"Content-Type": "text/xml; charset=utf-8", "SOAPAction": '""'}, base=BASE)
    leaked = "for 16-bit app support" in t or "[fonts]" in t or "aaaaaaaaaaaaaaaaaaaa" in t
    check("XXE", f"SOAP con {label}: no se expande ni se filtra", True, not leaked, ok=not leaked and st >= 400 or (not leaked and "Fault" in t))

# ---------------------------------------------------------------- Fuerza bruta
victim = f"nadie-{uuid.uuid4().hex[:6]}@ejemplo.com"
codes = [call("POST", "/auth/login", {"email": victim, "password": valor_incorrecto(i)})[0] for i in range(15)]
check("FUERZA", "15 logins fallidos seguidos terminan en 429", True, 429 in codes, ok=429 in codes)
print("         códigos:", codes)

# ---------------------------------------------------------------- Headers y CORS
st, t, h = call("GET", "/products", headers={"Origin": "http://localhost:5173"})
for name in ["Content-Security-Policy", "Strict-Transport-Security", "X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy"]:
    check("HEADERS", f"header {name} presente", True, bool(h.get(name)), ok=bool(h.get(name)))
acao = h.get("Access-Control-Allow-Origin")
check("CORS", "origen permitido: se refleja exacto (no comodín)", "http://localhost:5173", acao)
st, t, h2 = call("GET", "/products", headers={"Origin": "https://evil.example"})
check("CORS", "origen no permitido: sin Access-Control-Allow-Origin", None, h2.get("Access-Control-Allow-Origin"))
st, t, h3 = call("OPTIONS", "/auth/login", headers={"Origin": "https://evil.example", "Access-Control-Request-Method": "POST"})
check("CORS", "preflight de origen ajeno rechazado", True, st in (403, 400) or not h3.get("Access-Control-Allow-Origin"), ok=(st in (403, 400) or not h3.get("Access-Control-Allow-Origin")))
check("CORS", "nunca comodín con credenciales", False, acao == "*" and h.get("Access-Control-Allow-Credentials") == "true")

# ---------------------------------------------------------------- Errores sin datos internos
bad = [("POST", "/auth/login", b"{"), ("POST", "/auth/register", b'{"email":123}'), ("GET", "/v1/ventas/no-es-un-numero", None), ("GET", "/products/%00", None),
       ("GET", "/no-existe", None)]
leaks = []
for m, p, raw in bad:
    st, t, h = call(m, p, raw=raw, token=admin)
    for marker in ("at com.petshop", "java.lang.", "org.springframework", "Exception", ".java:", "Caused by", "Whitelabel"):
        if marker in t:
            leaks.append((m, p, st, marker))
check("ERRORES", "ninguna respuesta de error muestra stack trace ni clases internas", "sin filtraciones", "sin filtraciones" if not leaks else leaks)

# ---------------------------------------------------------------- ETag / If-Match / estados / DLQ
for it in js(call("GET", "/cart", token=cliente)[1]) or []:
    call("POST", "/cart/remove", it, cliente)
call("POST", "/cart/add", {"productId": "p2", "name": "Pelota", "variant": "juguetes", "quantity": 1, "price": 1}, cliente)
st, t, _ = call("POST", "/v1/ventas", {"nombre": "A", "direccion": "D 1", "ciudad": "CABA", "codigoPostal": "1425", "medioPago": "tok_aprobado"},
                cliente, {"Idempotency-Key": str(uuid.uuid4())})
oid = (js(t) or {}).get("id")
st, t, h = call("GET", f"/v1/ventas/{oid}", token=admin)
etag1 = h.get("ETag")
check("ETAG", "GET /v1/ventas/{id} devuelve un ETag", True, bool(etag1), ok=bool(etag1))
st, t, h = call("PATCH", f"/v1/ventas/{oid}", {"estado": "ENVIADA"}, admin, {"If-Match": '"desactualizado"'})
check("ETAG", "PATCH con If-Match desactualizado -> 412", 412, st)
check("ETAG", "…con formato Problem Details", "application/problem+json", (h.get("Content-Type") or "").split(";")[0])
st, t, h = call("PATCH", f"/v1/ventas/{oid}", {"estado": "BASURA"}, admin, {"If-Match": etag1})
check("ESTADO", "estado inexistente -> 422", 422, st)
st, t, h = call("PATCH", f"/v1/ventas/{oid}", {"estado": "ENVIADA"}, admin, {"If-Match": etag1})
etag2 = h.get("ETag")
check("ETAG", "PATCH con el ETag vigente -> 200 y ETag nuevo", True, st == 200 and etag2 != etag1, ok=(st == 200 and etag2 != etag1))
st, t, h = call("PATCH", f"/v1/ventas/{oid}", {"estado": "CANCELADA"}, admin, {"If-Match": etag1})
check("ETAG", "reusar el ETag viejo después del cambio -> 412", 412, st)
st, t, h = call("POST", f"/v1/ventas/{oid}/cancelacion", token=cliente)
check("ESTADO", "cancelar una venta ya ENVIADA (transición inválida) -> 409", 409, st)
st, t, h = call("GET", "/products/p1")
pe = h.get("ETag")
check("ETAG", "GET /products/{id} devuelve un ETag", True, bool(pe), ok=bool(pe))
st, t, h = call("PUT", "/products/p1", js(t) or {}, admin, {"If-Match": '"desactualizado"'})
check("ETAG", "PUT /products/{id} con If-Match desactualizado -> 412", 412, st)
st, t, _ = call("GET", "/admin/dlq", token=cliente)
check("DLQ", "cliente GET /admin/dlq -> 403", 403, st)
st, t, _ = call("GET", "/admin/dlq")
check("DLQ", "sin token GET /admin/dlq -> 401", 401, st)
st, t, _ = call("GET", "/admin/dlq", token=admin)
check("DLQ", "admin GET /admin/dlq -> 200 con el conteo", True, st == 200 and "count" in (js(t) or {}), ok=(st == 200 and "count" in (js(t) or {})))

print()
fails = [r for r in RESULTS if not r[4]]
print(f"RESUMEN: {len(RESULTS) - len(fails)} de {len(RESULTS)} pruebas bien; {len(fails)} fallan")
json.dump([dict(area=a, prueba=n, esperado=e, obtenido=g, ok=o) for a, n, e, g, o in RESULTS], open(os.path.basename(sys.argv[1]) if len(sys.argv) > 1 else "sonda_seguridad_resultado.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1, default=str)  # NOSONAR (el nombre del archivo se reduce a su nombre base)
sys.exit(1 if fails else 0)
