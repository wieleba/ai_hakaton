import { defineConfig } from '@playwright/test';

// E2E tests run against the live docker-compose stack at http://localhost:3000.
// Bring it up first: `docker compose up -d` from repo root.
//
// We use the system Chrome via `channel: 'chrome'` so we don't download
// Playwright's bundled Chromium (~150MB). Set PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
// when installing if you want to skip that download too.

export default defineConfig({
  testDir: './e2e',
  // Tests share backend state (users, friendships). Keep them serial.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: process.env.CI ? 'github' : 'list',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    channel: 'chrome',
    headless: true,
    launchOptions: {
      args: ['--no-sandbox', '--disable-dev-shm-usage'],
    },
  },
});
