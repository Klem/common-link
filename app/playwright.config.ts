import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  use: {
    baseURL: 'https://localhost:3000',
    // `npm run dev` serves a locally generated certificate.
    ignoreHTTPSErrors: true,
    locale: 'fr',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'https://localhost:3000',
    ignoreHTTPSErrors: true,
    reuseExistingServer: true,
    timeout: 120000,
  },
});
