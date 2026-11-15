import { defineConfig } from "@playwright/test";

/**
 * Pruebas de punta a punta contra el sistema en ejecución (tienda en 5173 y API en 8080), con un navegador real.
 *
 *   npx playwright test                 # contra http://localhost:5173
 *   E2E_BASE_URL=https://mi-tienda npx playwright test
 *   PW_CHANNEL=chrome npx playwright test   # usa el Chrome instalado en vez de descargar Chromium
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0, // sin reintentos: una prueba inestable tiene que verse, no taparse
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    channel: process.env.PW_CHANNEL || undefined,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    locale: "es-AR",
  },
});
