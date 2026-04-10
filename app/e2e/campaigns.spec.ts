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

test.describe('Campaigns page', () => {
  // ── Page load ──────────────────────────────────────────────────────────────

  test('Campaigns page loads for association user', async ({ page, context }) => {
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

    await page.route(`${API_BASE}/api/association/campaigns`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      }),
    );

    await page.goto('/fr/dashboard/association/campaigns');

    await expect(page).toHaveURL(/\/fr\/dashboard\/association\/campaigns/, { timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Campagnes' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Aucune campagne')).toBeVisible({ timeout: 10000 });
  });

  // ── Sidebar active state ───────────────────────────────────────────────────

  test('Sidebar campaigns link is active on campaigns page', async ({ page, context }) => {
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

    await page.route(`${API_BASE}/api/association/campaigns`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      }),
    );

    await page.goto('/fr/dashboard/association/campaigns');

    await expect(page).toHaveURL(/\/fr\/dashboard\/association\/campaigns/, { timeout: 10000 });

    // The 🎯 Campagnes nav link should have the active class (text-green)
    const campaignsLink = page.getByRole('link', { name: /Campagnes/ });
    await expect(campaignsLink).toHaveClass(/text-green/, { timeout: 10000 });
  });
});
