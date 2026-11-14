import { expect, test } from "@playwright/test";
import { addFirstProductToCart, CUSTOMER, emptyCart, fillCheckout, login } from "./helpers";

/** Escenarios críticos 1 a 4: catálogo, compra de invitado, pago rechazado y compra de un cliente con su historial. */

test.describe("catálogo", () => {
  test("E2E-01: se explora el catálogo, se filtra por categoría y se busca por nombre", async ({ page }) => {
    await page.goto("/productos");
    await expect(page.getByRole("heading", { level: 1, name: "Todos los productos" })).toBeVisible();
    const total = await page.getByRole("button", { name: "Agregar al carrito" }).count();
    expect(total).toBeGreaterThan(0);

    await page.getByRole("button", { name: "Juguetes" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Juguetes" })).toBeVisible();
    await expect(page).toHaveURL(/categoria=juguetes/);

    await page.getByPlaceholder("Nombre o marca...").fill("pelota");
    await expect(page.getByRole("heading", { level: 3, name: /Pelota/ }).first()).toBeVisible();
    await expect(page).toHaveURL(/q=pelota/);

    await page.getByRole("heading", { level: 3, name: /Pelota/ }).first().click();
    await expect(page).toHaveURL(/\/productos\/p\d+/);
    await expect(page.getByRole("heading", { level: 1 })).toContainText("Pelota");
  });

  test("E2E-01b: una dirección de producto que no existe lo informa sin romper la tienda", async ({ page }) => {
    await page.goto("/productos/no-existe");
    await expect(page.getByText("No se encontró el producto.")).toBeVisible();
  });
});

test.describe("compra", () => {
  test("E2E-02: un invitado calcula el envío por código postal y compra con tarjeta aprobada", async ({ page }) => {
    await emptyCart(page);
    const name = await addFirstProductToCart(page);

    await page.goto("/carrito");
    await expect(page.getByRole("heading", { level: 3, name })).toBeVisible();
    await fillCheckout(page, { codigoPostal: "1425", medioPago: "tok_aprobado" });
    await expect(page.locator(".shipping-note.ok")).toContainText(/CABA|1\.?200|Gratis/i);

    await page.getByRole("button", { name: "Finalizar compra" }).click();
    await expect(page.getByRole("heading", { name: "¡Compra confirmada!" })).toBeVisible();
    await expect(page.getByText(/Tu pedido #\d+ se registró con éxito/)).toBeVisible();
  });

  test("E2E-03: con una tarjeta rechazada no se cobra, se avisa y el carrito se conserva", async ({ page }) => {
    await emptyCart(page);
    const name = await addFirstProductToCart(page);

    await page.goto("/carrito");
    await fillCheckout(page, { medioPago: "tok_rechazado" });
    await page.getByRole("button", { name: "Finalizar compra" }).click();

    await expect(page.getByRole("status").filter({ hasText: /rechaz|fondos|pago/i }).first()).toBeVisible();
    await expect(page.getByRole("heading", { name: "¡Compra confirmada!" })).toHaveCount(0);
    await page.goto("/carrito");
    await expect(page.getByRole("heading", { level: 3, name })).toBeVisible();
  });

  test("E2E-04: un código postal inválido no deja finalizar la compra", async ({ page }) => {
    await emptyCart(page);
    await addFirstProductToCart(page);

    await page.goto("/carrito");
    await fillCheckout(page, { codigoPostal: "12", medioPago: "tok_aprobado" });
    await page.getByRole("button", { name: "Finalizar compra" }).click();

    await expect(page.getByRole("heading", { name: "¡Compra confirmada!" })).toHaveCount(0);
    await expect(page).toHaveURL(/\/carrito/);
  });
});

test.describe("cliente con sesión", () => {
  test("E2E-05: un cliente inicia sesión, compra y ve su pedido en Mis pedidos", async ({ page }) => {
    await login(page, CUSTOMER);
    await emptyCart(page);
    await addFirstProductToCart(page);

    await page.goto("/carrito");
    await fillCheckout(page, { medioPago: "tok_aprobado" });
    await page.getByRole("button", { name: "Finalizar compra" }).click();
    await expect(page.getByRole("heading", { name: "¡Compra confirmada!" })).toBeVisible();
    const orderNumber = (await page.getByText(/Tu pedido #\d+/).innerText()).match(/#(\d+)/)![1];

    await page.goto("/mis-pedidos");
    await expect(page.getByRole("heading", { name: "Mis pedidos" })).toBeVisible();
    await expect(page.getByText(`#${orderNumber}`).first()).toBeVisible();
  });

  test("E2E-06: quien no inició sesión es llevado al ingreso y vuelve a donde quería ir", async ({ page }) => {
    await page.goto("/mis-pedidos");
    await expect(page).toHaveURL(/\/ingresar\?next=%2Fmis-pedidos/);
    await page.getByLabel("Email").fill(CUSTOMER.email);
    await page.getByLabel("Contraseña").fill(CUSTOMER.password);
    await page.locator("form").getByRole("button", { name: "Iniciar sesión" }).click();
    await expect(page).toHaveURL(/\/mis-pedidos/);
  });

  test("E2E-07: una contraseña incorrecta muestra el error y no inicia sesión", async ({ page }) => {
    await page.goto("/ingresar");
    await page.getByLabel("Email").fill(CUSTOMER.email);
    await page.getByLabel("Contraseña").fill("incorrecta");
    await page.locator("form").getByRole("button", { name: "Iniciar sesión" }).click();
    await expect(page.getByRole("alert")).toBeVisible();
    await expect(page).toHaveURL(/\/ingresar/);
  });
});
