"""Demostración en vivo de la 2.ª entrega: control de acceso por rol (401/403) y patrón State de la venta (409).

Con el backend en ejecución:
    python tests-api/demo_entrega2.py        # desde app/

Variables opcionales: PETSHOP_URL, ADMIN_EMAIL, ADMIN_PASSWORD, CUSTOMER_EMAIL, CUSTOMER_PASSWORD (los de desarrollo del README).
Solo usa la API pública; el único cambio que hace es pasar a ENVIADA un pedido que estaba COMPLETADA.
"""
import json
import os
import sys
import urllib.error
import urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

API = os.environ.get("PETSHOP_URL", "http://localhost:8080").rstrip("/")


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["X-Auth-Token"] = token
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.headers.get("Content-Type", ""), response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.headers.get("Content-Type", ""), error.read().decode("utf-8", "replace")
    except urllib.error.URLError as error:
        sys.exit(f"No se pudo conectar con {API}: ¿está el backend en ejecución? ({error.reason})")


def login(email, secret):
    status, _, text = call("POST", "/api/auth/login", {"email": email, "password": secret})
    if status != 200:
        sys.exit(f"No se pudo ingresar como {email} (código {status}).")
    return json.loads(text)["token"]


def mostrar(titulo, esperado, status, content_type="", detalle=""):
    marca = "OK " if status == esperado else "MAL"
    extra = f"  [{content_type.split(';')[0]}]" if content_type and status >= 400 else ""
    print(f"  [{marca}] {titulo:<58} -> {status} (esperado {esperado}){extra}")
    if detalle:
        print(f"        {detalle}")
    return status == esperado


admin = login(os.environ.get("ADMIN_EMAIL", "admin@petshop.com"), os.environ.get("ADMIN_PASSWORD", "admin123"))
cliente = login(os.environ.get("CUSTOMER_EMAIL", "cliente@ejemplo.com"), os.environ.get("CUSTOMER_PASSWORD", "password"))
resultados = []

print("\n1) Seguridad: JWT y control de acceso por rol (ADR 0011)")
resultados.append(mostrar("GET /api/orders sin sesión", 401, call("GET", "/api/orders")[0]))
resultados.append(mostrar("GET /api/orders con la sesión de un cliente", 403, call("GET", "/api/orders", token=cliente)[0]))
resultados.append(mostrar("GET /api/orders con la sesión de un administrador", 200, call("GET", "/api/orders", token=admin)[0]))
resultados.append(mostrar("GET /api/orders con un token alterado", 401, call("GET", "/api/orders", token=admin[:-3] + "xyz")[0]))

print("\n2) Patrón State: transiciones de una venta (ADR 0003)")
_, _, texto = call("GET", "/api/orders", token=admin)
completadas = [o for o in json.loads(texto) if o["estado"] == "COMPLETADA"]
if not completadas:
    sys.exit("  No hay ningún pedido COMPLETADA: hacé una compra en la tienda (tarjeta de prueba aprobada) y volvé a correr el script.")
venta = completadas[0]["id"]
print(f"  Venta usada: #{venta} (estado COMPLETADA)")


def cambiar(estado):
    status, content_type, text = call("PATCH", f"/api/v1/ventas/{venta}", {"estado": estado}, admin)
    detalle = ""
    if status >= 400:
        problema = json.loads(text)
        detalle = f"{problema.get('type')} | {problema.get('detail')}"
    return status, content_type, detalle


for titulo, estado, esperado in (
    ("COMPLETADA -> ENVIADA (permitido)", "ENVIADA", 200),
    ("ENVIADA -> ENVIADA (ya enviada)", "ENVIADA", 409),
    ("ENVIADA -> CANCELADA (estado final)", "CANCELADA", 409),
    ("Estado que no existe", "EN_LA_LUNA", 422),
):
    status, content_type, detalle = cambiar(estado)
    resultados.append(mostrar(titulo, esperado, status, content_type, detalle))

status, content_type, text = call("PUT", f"/api/orders/{venta}/status", {"estado": "COMPLETADA"}, admin)
resultados.append(mostrar("Ruta vieja del panel: ENVIADA -> COMPLETADA", 422, status, content_type))
status, content_type, text = call("PUT", f"/api/orders/{venta}/status", {"estado": "ENVIADA"}, admin)
resultados.append(mostrar("Ruta vieja del panel: ENVIADA -> ENVIADA", 409, status, content_type,
                          json.loads(text).get("detail", "") if status >= 400 else ""))

print(f"\nRESUMEN: {sum(resultados)} de {len(resultados)} comprobaciones bien")
sys.exit(0 if all(resultados) else 1)
