import { test, expect, BrowserContext } from '@playwright/test';

const API_BASE = 'http://localhost:8080';

const mockDonorUser = {
  id: 'e2e-donor-1',
  email: 'donor@example.com',
  role: 'DONOR',
  displayName: 'Jean Dupont',
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: new Date().toISOString(),
};

const mockAssoUser = {
  id: 'e2e-asso-1',
  email: 'asso@example.com',
  role: 'ASSOCIATION',
  displayName: 'MSF',
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: new Date().toISOString(),
};

/** Set the auth-session and cl-refresh cookies so the middleware and AuthProvider see an authenticated user. */
async function setAuthCookies(
  context: BrowserContext,
  role: 'DONOR' | 'ASSOCIATION',
  userId: string,
) {
  await context.addCookies([
    {
      name: 'auth-session',
      value: JSON.stringify({ userId, role }),
      domain: 'localhost',
      path: '/',
    },
    {
      name: 'cl-refresh',
      value: 'e2e-refresh-token',
      domain: 'localhost',
      path: '/',
    },
  ]);
}

test.describe('Dashboard smoke tests', () => {
  // ── Donor dashboard ────────────────────────────────────────────────────────

  test('authenticated DONOR loads /dashboard/donor', async ({ page, context }) => {
    await setAuthCookies(context, 'DONOR', mockDonorUser.id);

    await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
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

    await page.goto('/fr/dashboard/donor');

    await expect(page).toHaveURL(/\/fr\/dashboard\/donor/);
    await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Total donné')).toBeVisible();
  });

  // ── Association dashboard ──────────────────────────────────────────────────

  test('authenticated ASSOCIATION loads /dashboard/association', async ({ page, context }) => {
    await setAuthCookies(context, 'ASSOCIATION', mockAssoUser.id);

    await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
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

    await page.goto('/fr/dashboard/association');

    await expect(page).toHaveURL(/\/fr\/dashboard\/association/);
    await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Fonds collectés')).toBeVisible();
  });

  // ── Login-flow redirects to dashboard ─────────────────────────────────────

  test('donor dashboard loads after login', async ({ page }) => {
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
    await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
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
    await page.fill('input[type="email"]', mockDonorUser.email);
    await page.fill('input[type="password"]', 'password123');
    await page.getByRole('button', { name: /se connecter/i }).click();

    await expect(page).toHaveURL(/\/fr\/dashboard\/donor/, { timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Total donné')).toBeVisible();
    await expect(page.getByText('Dons effectués')).toBeVisible();
    await expect(page.getByText('Campagnes suivies')).toBeVisible();
    await expect(page.getByText("Score d'impact")).toBeVisible();
  });

  test('association dashboard loads after login', async ({ page }) => {
    await page.route(`${API_BASE}/api/auth/login`, (route) =>
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
    await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
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

    await page.goto('/fr/login');
    await page.fill('input[type="email"]', mockAssoUser.email);
    await page.fill('input[type="password"]', 'password123');
    await page.getByRole('button', { name: /se connecter/i }).click();

    await expect(page).toHaveURL(/\/fr\/dashboard\/association/, { timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Fonds collectés')).toBeVisible();
  });

  // ── Profile page ───────────────────────────────────────────────────────────

  test('donor profile page is accessible', async ({ page, context }) => {
    await setAuthCookies(context, 'DONOR', mockDonorUser.id);

    await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
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
    await page.route(`${API_BASE}/api/donor/me`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: mockDonorUser.id, displayName: 'Jean Dupont', anonymous: false }),
      }),
    );

    await page.goto('/fr/dashboard/donor/profile');

    await expect(page).toHaveURL(/\/fr\/dashboard\/donor\/profile/, { timeout: 5000 });
    await expect(page.locator('input[type="email"]')).toBeVisible({ timeout: 10000 });
  });

  // ── Role-based redirects ───────────────────────────────────────────────────

  test('DONOR accessing /dashboard/association is redirected to /dashboard/donor', async ({
    page,
    context,
  }) => {
    await setAuthCookies(context, 'DONOR', mockDonorUser.id);

    // Refresh returns 401 — we only care about the middleware redirect, not the page content
    await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
      route.fulfill({ status: 401, body: '{}' }),
    );

    await page.goto('/fr/dashboard/association');

    await expect(page).toHaveURL(/\/fr\/dashboard\/donor/, { timeout: 5000 });
  });

  test('ASSOCIATION accessing /dashboard/donor is redirected to /dashboard/association', async ({
    page,
    context,
  }) => {
    await setAuthCookies(context, 'ASSOCIATION', mockAssoUser.id);

    await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
      route.fulfill({ status: 401, body: '{}' }),
    );

    await page.goto('/fr/dashboard/donor');

    await expect(page).toHaveURL(/\/fr\/dashboard\/association/, { timeout: 5000 });
  });

  // ── Unauthenticated redirect ───────────────────────────────────────────────

  test('unauthenticated user accessing /dashboard/donor is redirected to /login', async ({
    page,
  }) => {
    // No cookies set — middleware should redirect to login
    await page.route(`${API_BASE}/api/auth/refresh`, (route) =>
      route.fulfill({ status: 401, body: '{}' }),
    );

    await page.goto('/fr/dashboard/donor');

    await expect(page).toHaveURL(/\/fr\/login/, { timeout: 5000 });
  });
});
