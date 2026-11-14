import { expect, type Page } from "@playwright/test";

export const API = process.env.E2E_API_URL ?? "http://localhost:8080/api";

/** Usuarios de desarrollo que carga el backend al arrancar (README). */
export const ADMIN = { email: "admin@petshop.com", password: "admin123" };
export const CUSTOMER = { email: "cliente@ejemplo.com", password: "password" };

/** Inicia sesión desde la pantalla de ingreso, como lo haría una persona. */
export async function login(page: Page, user: { email: string; password: string }) {
  await page.goto("/ingresar");
  await page.getByLabel("Email").fill(user.email);
  await page.getByLabel("Contraseña").fill(user.password);
  await page.locator("form").getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).not.toHaveURL(/\/ingresar/);
}

/** Agrega al carrito la primera tarjeta que tenga el botón y devuelve el nombre del producto. */
export async function addFirstProductToCart(page: Page): Promise<string> {
  await page.goto("/productos");
  const card = page.locator("article", { has: page.getByRole("button", { name: "Agregar al carrito" }) }).first();
  await expect(card).toBeVisible();
  const name = (await card.getByRole("heading", { level: 3 }).innerText()).trim();
  await card.getByRole("button", { name: "Agregar al carrito" }).click();
  await expect(page.getByRole("button", { name: /Carrito \([1-9]/ })).toBeVisible();
  return name;
}

/** Completa el formulario de envío y pago del carrito. */
export async function fillCheckout(page: Page, opts: { codigoPostal?: string; medioPago: string }) {
  await page.getByPlaceholder("Tu código postal").fill(opts.codigoPostal ?? "1425");
  await page.getByLabel("Nombre y apellido").fill("Persona de Prueba");
  await page.getByLabel("Dirección").fill("Calle Falsa 123");
  await page.getByLabel("Ciudad").fill("CABA");
  await page.getByLabel(/Medio de pago/).selectOption(opts.medioPago);
}

/** Vacía el carrito del invitado o del usuario (cada prueba parte de un carrito limpio). */
export async function emptyCart(page: Page) {
  await page.goto("/carrito");
  for (let i = 0; i < 20; i++) {
    const remove = page.getByRole("button", { name: /Quitar|Eliminar/ }).first();
    if (!(await remove.isVisible().catch(() => false))) return;
    await remove.click();
  }
}
