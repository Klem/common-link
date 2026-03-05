import { test, expect } from '@playwright/test';

const API_BASE = 'http://localhost:8080';

const mockUser = {
  id: 'e2e-user-1',
  email: 'test@example.com',
  role: 'DONOR',
  displayName: 'E2E User',
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: new Date().toISOString(),
};

test.beforeEach(async ({ page }) => {
  // Prevent hydration from attempting a real refresh
  await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
    route.fulfill({ status: 401, body: '{}' }),
  );
});

test.describe('Auth smoke tests', () => {
  test('email login — fills form, submits, and redirects to dashboard', async ({ page }) => {
    await page.route(`${API_BASE}/api/auth/login`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'test-access-token',
          refreshToken: 'test-refresh-token',
          user: mockUser,
        }),
      }),
    );

    await page.goto('/fr/login');

    await page.fill('input[type="email"]', 'test@example.com');
    await page.fill('input[type="password"]', 'password123');

    const submitButton = page.locator('button[type="submit"]');
    await expect(submitButton).toBeEnabled();
    await submitButton.click();

    await expect(page).toHaveURL(/\/fr\/dashboard/, { timeout: 10000 });
  });

  test('magic link signup (donor) — shows sent confirmation', async ({ page }) => {
    await page.route(`${API_BASE}/api/auth/magic-link/request`, (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }),
    );

    await page.goto('/fr/login?view=signup');

    // Select Donor role
    await page.getByRole('button', { name: /donateur/i }).click();

    // Fill email in MagicLinkForm
    const emailInput = page.locator('input[type="email"]').last();
    await emailInput.fill('donor@example.com');

    // Click the magic link submit button
    await page.getByRole('button', { name: /s'inscrire par lien magique/i }).click();

    // Verify sent state appears
    await expect(page.getByText(/Lien envoyé/)).toBeVisible({ timeout: 5000 });
  });

  test('Google button is present on login page', async ({ page }) => {
    await page.goto('/fr/login');
    await expect(page.getByRole('button', { name: /continuer avec google/i })).toBeVisible();
  });

  test('Google button is present on signup page (donor role)', async ({ page }) => {
    await page.goto('/fr/login?view=signup');
    // Select Donor role to reveal the Google button
    await page.getByRole('button', { name: /donateur/i }).click();
    await expect(page.getByRole('button', { name: "S'inscrire avec Google" })).toBeVisible();
  });
});
