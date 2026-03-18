import { test, expect } from '@playwright/test';

const API_BASE = 'http://localhost:8080';

const mockDonorUser = {
  id: 'e2e-user-1',
  email: 'test@example.com',
  role: 'DONOR',
  displayName: 'E2E User',
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: new Date().toISOString(),
};

const mockAssoUser = {
  id: 'e2e-user-2',
  email: 'asso@example.com',
  role: 'ASSOCIATION',
  displayName: 'E2E Asso',
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
          user: mockDonorUser,
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

    // Fill email in MagicLinkForm (first email input — MagicLinkForm appears before EmailRegisterForm)
    const emailInput = page.locator('input[type="email"]').first();
    await emailInput.fill('donor@example.com');

    // Click the magic link submit button
    await page.getByRole('button', { name: /s'inscrire par lien magique/i }).click();

    // Verify sent state appears
    await expect(page.getByText(/Lien envoyé/)).toBeVisible({ timeout: 5000 });
  });

  test('email signup (donor) — fills form, submits, and redirects to dashboard', async ({
    page,
  }) => {
    await page.route(`${API_BASE}/api/auth/register`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'test-access-token',
          refreshToken: 'test-refresh-token',
          user: mockDonorUser,
        }),
      }),
    );

    await page.goto('/fr/login?view=signup');

    // Select Donor role
    await page.getByRole('button', { name: /donateur/i }).click();

    // Fill the EmailRegisterForm (email input is the last one, after MagicLinkForm's)
    const emailInput = page.locator('input[type="email"]').last();
    await emailInput.fill('newdonor@example.com');

    const passwordInputs = page.locator('input[type="password"]');
    await passwordInputs.nth(0).fill('securepass1');
    await passwordInputs.nth(1).fill('securepass1');

    // Click the register submit button
    await page.getByRole('button', { name: /créer mon compte/i }).click();

    await expect(page).toHaveURL(/\/fr\/dashboard/, { timeout: 10000 });
  });

  test('email signup (association) — fills form and reaches profile step', async ({ page }) => {
    // Mock association search API
    await page.route('https://recherche-entreprises.api.gouv.fr/**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          results: [
            {
              nom_complet: 'Association Test',
              siren: '123456789',
              siege: { libelle_commune: 'Paris', code_postal: '75001' },
              etat_administratif: 'A',
            },
          ],
          total_results: 1,
        }),
      }),
    );

    await page.route(`${API_BASE}/api/auth/register`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'test-access-token',
          refreshToken: 'test-refresh-token',
          user: mockAssoUser,
        }),
      }),
    );

    await page.goto('/fr/login?view=signup');

    // Select Association role
    await page.getByRole('button', { name: /association/i }).click();

    // Step 1 — Search and select an association
    await page.fill('input[type="search"], input[type="text"]', 'Association Test');
    await page.getByRole('button', { name: /sélectionner/i }).click();

    // Step 2 — Fill email+password register form
    const emailInput = page.locator('input[type="email"]').last();
    await emailInput.fill('asso@example.com');

    const passwordInputs = page.locator('input[type="password"]');
    await passwordInputs.nth(0).fill('securepass1');
    await passwordInputs.nth(1).fill('securepass1');

    await page.getByRole('button', { name: /continuer/i }).click();

    // Should now be on step 3 (profile form)
    await expect(
      page.getByRole('button', { name: /créer mon espace/i }),
    ).toBeVisible({ timeout: 5000 });
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

  test('email register form is visible on donor signup', async ({ page }) => {
    await page.goto('/fr/login?view=signup');
    await page.getByRole('button', { name: /donateur/i }).click();
    await expect(page.getByRole('button', { name: /créer mon compte/i })).toBeVisible();
  });
});
