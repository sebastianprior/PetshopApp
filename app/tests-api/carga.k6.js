// Prueba de carga con k6: 50 usuarios concurrentes durante 5 minutos sobre los 3 endpoints críticos de la tienda.
//
//   catálogo  GET  /api/products        (lo que ve cualquiera que entra a la tienda)
//   login     POST /api/auth/login      (iniciar sesión; BCrypt es lento a propósito, por eso una vez cada 10 pedidos)
//   checkout  POST /api/v1/ventas       (comprar como invitado con la tarjeta de prueba aprobada)
//
// Criterio de la rúbrica: p95 < 500 ms y tasa de error < 1 % en cada uno. Si no se cumple, k6 termina con error.
//
//   k6 run app/tests-api/carga.k6.js                              # contra http://localhost:8080
//   k6 run -e BASE_URL=https://mi-api -e VUS=50 -e DURATION=5m app/tests-api/carga.k6.js
//
// SOLO PARA ENTORNOS DE PRUEBA: el arranque sube el stock de un producto para que alcance durante toda la carga y la
// prueba crea miles de ventas de invitado.
import http from "k6/http";
import { check, sleep } from "k6";

const BASE = (__ENV.BASE_URL || "http://localhost:8080") + "/api";
const ADMIN = { email: __ENV.ADMIN_EMAIL || "admin@petshop.com", password: __ENV.ADMIN_PASSWORD || "admin123" };
const CUSTOMER = { email: __ENV.CUSTOMER_EMAIL || "cliente@ejemplo.com", password: __ENV.CUSTOMER_PASSWORD || "password" };
const JSON_HEADERS = { "Content-Type": "application/json" };

export const options = {
  scenarios: {
    carga: { executor: "constant-vus", vus: Number(__ENV.VUS || 50), duration: __ENV.DURATION || "5m" },
  },
  thresholds: {
    "http_req_duration{endpoint:catalogo}": ["p(95)<500"],
    "http_req_duration{endpoint:login}": ["p(95)<500"],
    "http_req_duration{endpoint:checkout}": ["p(95)<500"],
    "http_req_failed{endpoint:catalogo}": ["rate<0.01"],
    "http_req_failed{endpoint:login}": ["rate<0.01"],
    "http_req_failed{endpoint:checkout}": ["rate<0.01"],
  },
};

export function setup() {
  const login = http.post(`${BASE}/auth/login`, JSON.stringify(ADMIN), { headers: JSON_HEADERS });
  const token = login.json("token");
  const product = http.get(`${BASE}/products/p2`).json();
  product.stock = 1000000; // alcanza para toda la prueba
  http.put(`${BASE}/products/p2`, JSON.stringify(product), { headers: { ...JSON_HEADERS, "X-Auth-Token": token } });
}

function uuid() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export default function () {
  const guest = `k6-${__VU}`;

  const catalogo = http.get(`${BASE}/products?category=juguetes`, { tags: { endpoint: "catalogo" } });
  check(catalogo, { "catálogo 200": (r) => r.status === 200 });

  if (__ITER % 10 === 0) {
    const login = http.post(`${BASE}/auth/login`, JSON.stringify(CUSTOMER), { headers: JSON_HEADERS, tags: { endpoint: "login" } });
    check(login, { "login 200": (r) => r.status === 200 });
  }

  http.post(`${BASE}/cart/add`, JSON.stringify({ productId: "p2", name: "Pelota", variant: "juguetes", quantity: 1, price: 499 }),
    { headers: { ...JSON_HEADERS, "X-Guest-Id": guest } });

  const venta = http.post(
    `${BASE}/v1/ventas`,
    JSON.stringify({ nombre: "Carga", direccion: "Calle 1", ciudad: "CABA", codigoPostal: "1425", medioPago: "tok_aprobado" }),
    { headers: { ...JSON_HEADERS, "X-Guest-Id": guest, "Idempotency-Key": uuid() }, tags: { endpoint: "checkout" } },
  );
  check(venta, { "checkout 201": (r) => r.status === 201 || r.status === 200 || r.status === 202 });

  sleep(0.5 + Math.random());
}
