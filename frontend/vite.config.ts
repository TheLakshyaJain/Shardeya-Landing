import path from 'node:path'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // e2e/ holds Playwright specs (M2's browser-driven verification suite,
    // see e2e/README or playwright.config.ts) -- they use @playwright/test's
    // own `test`/`expect`, not Vitest's, and Vitest's default include glob
    // would otherwise pick them up and fail immediately on test.describe.configure().
    exclude: ['**/node_modules/**', '**/dist/**', '**/.{idea,git,cache,output,temp}/**', '**/e2e/**'],
  },
})
