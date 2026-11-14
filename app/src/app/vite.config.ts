/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: '0.0.0.0',
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './test/setup.ts',
    // Las pruebas de punta a punta (e2e/) las corre Playwright, no Vitest
    exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
  },
});
