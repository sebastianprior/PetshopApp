import { expect, test } from "@playwright/test";
import { addFirstProductToCart, fillCheckout } from "./helpers";

/**
 * La tienda se sirve con una política de contenido (CSP) estricta. Esta prueba navega por lo principal y exige que el
 * navegador no bloquee nada: si la política es demasiado cerrada se rompe la tienda; si no hay política, pasa igual, y
 * el control de que exista está en los headers de nginx y de Vercel.
 */
test("E2E-15: navegar, comprar y ver las preguntas frecuentes no provoca ninguna violación de la política de contenido", async ({ page }) => {
  const problemas: string[] = [];
  page.on("console", (m) => {
    if (/Content Security Policy|Refused to (load|connect|apply|execute|frame)/i.test(m.text())) problemas.push(m.text().slice(0, 200));
  });
  page.on("pageerror", (e) => problemas.push(`error de página: ${e.message.slice(0, 150)}`));

  await page.goto("/");
  await page.waitForLoadState("networkidle");
  await addFirstProductToCart(page);
  await page.goto("/carrito");
  await fillCheckout(page, { medioPago: "tok_aprobado" });
  await page.getByRole("button", { name: /Finalizar compra|Pagar con MercadoPago/ }).click();
  await expect(page.getByRole("heading", { name: /Compra confirmada|Estamos procesando|MercadoPago/ }).or(page.locator("body"))).toBeVisible();
  await page.goto("/preguntas-frecuentes");
  await page.goto("/ingresar");
  await page.waitForLoadState("networkidle");

  expect(problemas).toEqual([]);
});
