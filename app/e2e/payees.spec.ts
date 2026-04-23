import { test, expect, BrowserContext } from '@playwright/test';

const API_BASE = 'http://localhost:8080';

const mockAssoUser = {
  id: 'e2e-asso-1',
  email: 'asso@example.com',
  role: 'ASSOCIATION',
  displayName: 'MSF',
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: new Date().toISOString(),
};

/** Set the auth-session and cl-refresh cookies so middleware and AuthProvider see an authenticated user. */
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

test.describe('Payees page', () => {
  // ── Page load ──────────────────────────────────────────────────────────────

  test('Payees page loads for association user', async ({ page, context }) => {
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

    await page.route(`${API_BASE}/api/auth/me`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockAssoUser),
      }),
    );

    await page.route(`${API_BASE}/api/association/payees`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      }),
    );

    await page.goto('/fr/dashboard/association/payees');

    await expect(page).toHaveURL(/\/fr\/dashboard\/association\/payees/, { timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Bénéficiaires' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Aucun bénéficiaire sélectionné.')).toBeVisible({ timeout: 10000 });
  });

  // ── Search input ───────────────────────────────────────────────────────────

  test('Search input accepts only digits', async ({ page, context }) => {
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

    await page.route(`${API_BASE}/api/association/payees`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      }),
    );

    await page.goto('/fr/dashboard/association/payees');

    const input = page.locator('input[inputmode="numeric"]');
    await expect(input).toBeVisible({ timeout: 10000 });

    await input.pressSequentially('abc123');

    const value = await input.inputValue();
    expect(value).toBe('123');
  });

  // ── Sidebar active state ───────────────────────────────────────────────────

  test('Sidebar link is active on payees page', async ({ page, context }) => {
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

    await page.route(`${API_BASE}/api/association/payees`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      }),
    );

    await page.goto('/fr/dashboard/association/payees');

    // The active sidebar link has class bg-green/10 and text-green
    const payeesLink = page.locator('a[href*="payees"]').filter({ hasText: '🏦' });
    await expect(payeesLink).toBeVisible({ timeout: 10000 });
    await expect(payeesLink).toHaveClass(/bg-green\/10/);
  });
});
