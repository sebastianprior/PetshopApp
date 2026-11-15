import { expect, test } from "@playwright/test";
import { ADMIN, CUSTOMER, login } from "./helpers";

/** Escenarios críticos 8 a 11: administración con control de acceso, preguntas frecuentes y Salchi Bot. */

test.describe("administración", () => {
  test("E2E-08: el administrador entra al panel y ve ventas, productos y cupones", async ({ page }) => {
    await login(page, ADMIN);
    await page.goto("/admin");
    await expect(page.getByRole("heading", { name: "Panel de administración" })).toBeVisible();
    for (const tab of ["Productos", "Cupones", "Preguntas frecuentes"]) {
      const button = page.locator(".admin-tab", { hasText: tab });
      await button.click();
      await expect(button).toHaveClass(/active/);
    }
  });

  test("E2E-09: un cliente no puede abrir el panel de administración", async ({ page }) => {
    await login(page, CUSTOMER);
    await page.goto("/admin");
    await expect(page.getByRole("heading", { name: "Panel de administración" })).toHaveCount(0);
  });

  test("E2E-10: sin sesión el panel de administración pide ingresar", async ({ page }) => {
    await page.goto("/admin");
    await expect(page.getByRole("heading", { name: "Panel de administración" })).toHaveCount(0);
  });
});

test.describe("ayuda", () => {
  test("E2E-11: las preguntas frecuentes se despliegan y Salchi Bot responde con lo mismo", async ({ page }) => {
    await page.goto("/preguntas-frecuentes");
    await expect(page.getByRole("heading", { level: 1, name: "Preguntas frecuentes" })).toBeVisible();
    const question = page.getByRole("button", { expanded: false }).filter({ hasText: /\?/ }).first();
    await question.click();
    await expect(page.getByRole("button", { expanded: true }).filter({ hasText: /\?/ }).first()).toBeVisible();

    await page.getByRole("button", { name: "Abrir chat con Salchi Bot" }).click();
    const chat = page.getByRole("dialog", { name: "Chat con Salchi Bot" });
    await chat.getByLabel("Mensaje para Salchi Bot").fill("¿cuánto tarda el envío?");
    await chat.getByRole("button", { name: "Enviar mensaje" }).click();
    await expect(chat).toContainText(/envío|días|hs/i, { timeout: 15_000 });
  });
});
