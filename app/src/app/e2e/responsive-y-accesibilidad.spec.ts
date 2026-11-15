import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { addFirstProductToCart } from "./helpers";

/**
 * Responsive a 360, 768 y 1280 px (sin scroll horizontal, con lo principal visible) y accesibilidad con axe
 * (WCAG 2.2 AA): ninguna violación crítica ni seria.
 */

const SIZES = [
  { name: "360 (celular)", width: 360, height: 740 },
  { name: "768 (tablet)", width: 768, height: 1024 },
  { name: "1280 (escritorio)", width: 1280, height: 800 },
];
const PAGES = [
  { name: "inicio", path: "/" },
  { name: "productos", path: "/productos" },
  { name: "ingreso", path: "/ingresar" },
  { name: "preguntas frecuentes", path: "/preguntas-frecuentes" },
  { name: "carrito vacío", path: "/carrito" },
  { name: "detalle de producto", path: "/productos/p1" },
];

for (const size of SIZES) {
  test.describe(`responsive ${size.name}`, () => {
    test.use({ viewport: { width: size.width, height: size.height } });

    for (const p of PAGES) {
      test(`E2E-12: ${p.name} no tiene scroll horizontal`, async ({ page }) => {
        await page.goto(p.path);
        await page.waitForLoadState("networkidle");
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflow, `la página se desborda ${overflow}px`).toBeLessThanOrEqual(0);
      });
    }

    test("E2E-13: se puede agregar al carrito y llegar al formulario de compra", async ({ page }) => {
      await addFirstProductToCart(page);
      await page.goto("/carrito");
      await expect(page.getByLabel("Nombre y apellido")).toBeVisible();
      await expect(page.getByRole("button", { name: /Finalizar compra|Pagar con MercadoPago/ })).toBeVisible();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(0);
    });
  });
}

test.describe("accesibilidad (axe, WCAG 2.2 AA)", () => {
  for (const p of PAGES) {
    test(`E2E-14: ${p.name} sin violaciones críticas ni serias`, async ({ page }) => {
      await page.goto(p.path);
      await page.waitForLoadState("networkidle");
      const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
      const blocking = results.violations.filter((v) => v.impact === "critical" || v.impact === "serious");
      expect(
        blocking.map((v) => `${v.id} (${v.impact}): ${v.help} — ${v.nodes.length} elemento(s), ej. ${v.nodes[0]?.target.join(" ")}`),
      ).toEqual([]);
    });
  }
});
