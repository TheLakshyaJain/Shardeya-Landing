import { defineConfig, devices } from '@playwright/test';

// M2 verification suite -- drives the app through a real headless browser
// against a live backend (docker compose stack + `mvn spring-boot:run`),
// per CLAUDE.md's own testing strategy table ("Frontend integration |
// Playwright | Key flows"). Not wired into `npm test`/CI yet -- requires a
// live backend + docker stack, same precondition as the backend's own
// Testcontainers-based integration tests.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    // Overridable so a run can target an isolated verification stack (its own
    // ports, its own backend log for OTP reads) instead of whatever dev
    // server happens to be running on 5173 -- same pattern as this suite's
    // existing M2_BACKEND_LOG/M2_POSTGRES_CONTAINER env overrides.
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-360', use: { ...devices['Pixel 5'], viewport: { width: 360, height: 740 } } },
  ],
});
