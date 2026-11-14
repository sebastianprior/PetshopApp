"""Concurrencia: ¿se puede vender de más el último producto cuando varias personas compran a la vez?

Crea un producto propio con stock 1, lanza N compras de invitado al mismo tiempo (todas listas para disparar juntas) y exige que:
  - exactamente 1 compra salga bien,
  - las demás reciban un error de stock (409), nunca un 500,
  - el stock final sea 0 (no negativo) y haya una sola venta COMPLETADA con ese producto.
Después borra el producto. Ataca un backend EN EJECUCIÓN (PETSHOP_URL, por defecto http://localhost:8080); termina con código 1 si falla.
"""
import json, os, sys, threading, urllib.error, urllib.request, uuid

BASE = os.environ.get("PETSHOP_URL", "http://localhost:8080").rstrip("/") + "/api"
COMPRADORES = int(os.environ.get("COMPRADORES", "12"))


def call(method, path, body=None, headers=None):
    h = {"Content-Type": "application/json"}
    h.update(headers or {})
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode()
            return r.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw[:200]


admin = call("POST", "/auth/login", {"email": os.environ.get("ADMIN_EMAIL", "admin@petshop.com"), "password": os.environ.get("ADMIN_PASSWORD", "admin123")})[1]["token"]
auth = {"X-Auth-Token": admin}
status, producto = call("POST", "/products", {"name": "Ultima unidad (prueba de concurrencia)", "brand": "Prueba", "price": 1000, "stock": 1,
                                              "categoryId": "juguetes", "imageUrl": "/products/p1.svg", "rating": 4}, auth)
assert status == 200, f"no se pudo crear el producto de prueba: {status} {producto}"
pid = producto["id"]

resultados, listo = [], threading.Barrier(COMPRADORES)


def comprador(i):
    gid = f"concurrencia-{i}-{uuid.uuid4().hex[:6]}"
    call("POST", "/cart/add", {"productId": pid, "name": "Ultima unidad", "variant": "x", "quantity": 1, "price": 1000}, {"X-Guest-Id": gid})
    listo.wait()  # todos disparan la compra juntos
    code, body = call("POST", "/v1/ventas", {"nombre": "C", "direccion": "D 1", "ciudad": "CABA", "codigoPostal": "1425", "medioPago": "tok_aprobado"},
                      {"X-Guest-Id": gid, "Idempotency-Key": str(uuid.uuid4())})
    resultados.append((code, body))


hilos = [threading.Thread(target=comprador, args=(i,)) for i in range(COMPRADORES)]
[h.start() for h in hilos]
[h.join() for h in hilos]

codigos = sorted(c for c, _ in resultados)
stock = call("GET", f"/products/{pid}")[1]["stock"]
ventas = [o for o in (call("GET", "/orders", headers=auth)[1] or []) if any(i.get("productId") == pid for i in o.get("items", [])) and o.get("estado") in ("COMPLETADA", "ENVIADA")]
call("DELETE", f"/products/{pid}", headers=auth)

print(f"{COMPRADORES} compradores simultáneos del último producto -> códigos: {codigos}")
print(f"stock final: {stock} | ventas completadas con ese producto: {len(ventas)}")
fallas = []
if codigos.count(201) != 1:
    fallas.append(f"se esperaba exactamente 1 compra exitosa (201) y hubo {codigos.count(201)}")
if any(c >= 500 for c in codigos):
    fallas.append("hubo errores 500")
if stock != 0:
    fallas.append(f"el stock final tendría que ser 0 y es {stock}")
if len(ventas) != 1:
    fallas.append(f"tendría que haber 1 venta completada y hay {len(ventas)}")
print("RESULTADO:", "OK, no se vende de más" if not fallas else "FALLA: " + "; ".join(fallas))
sys.exit(1 if fallas else 0)
