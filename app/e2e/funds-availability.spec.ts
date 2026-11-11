import { test, expect, BrowserContext, Page, Route } from '@playwright/test';

/**
 * "Où en sont vos dons" (Payments tab, sprint FD6-06): a HelloAsso transfer on its way is confirmed as received, the
 * usable amount goes up, and a payment becomes possible. The API is simulated with `page.route` (in-memory backend),
 * like the other specs of this folder. Second journey: the same confirmation with the keyboard only.
 */

const API_BASE = 'http://localhost:8080';
const CAMPAIGN_ID = 'camp-e2e-funds';

const assoUser = {
  id: 'e2e-asso-funds',
  email: 'asso@example.com',
  role: 'ASSOCIATION',
  displayName: 'Les Amis du Jardin',
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: new Date().toISOString(),
};

const campaign = {
  id: CAMPAIGN_ID, name: 'Jardin partagé', emoji: '🌱', description: 'desc', goal: 1000, raised: 70, status: 'LIVE',
  startDate: null, endDate: null, milestones: [], budgetSections: [], category: null, actionPlace: null, reason: null,
  impactGoals: null, coverImage: null, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z',
};

const payee = {
  id: 'payee-1', payeeType: 'COMPANY', name: 'ACME Corp', identifier1: '123456789', identifier2: null, activityCode: null,
  category: null, city: 'Paris', postalCode: '75001', active: true, hasPayouts: false,
  ibans: [{ id: 'iban-1', iban: 'FR76 0000 0000', status: 'VERIFIED', vopResult: null, vopSuggestedName: null, verifiedAt: null, active: true }],
  createdAt: '2026-01-01T00:00:00Z',
};

/** In-memory funds side of the backend. */
interface FakeBackend {
  received: boolean;
  calls: string[];
}

function funds(b: FakeBackend) {
  return {
    provider: 'HELLOASSO', raised: 70, atProvider: 40, payoutPreparing: 0,
    inTransit: b.received ? 0 : 30, available: b.received ? 30 : 0, withdrawn: 0,
    paidOut: 0, pendingPayouts: 0, disbursable: b.received ? 30 : 0,
    cashOuts: [{
      idCashOut: 60001, cashOutDate: '2026-11-10', netTotal: 1001, campaignAmount: 30,
      state: b.received ? 'AVAILABLE' : 'IN_TRANSIT', autoAvailableOn: '2026-11-12',
      confirmedAt: b.received ? new Date().toISOString() : null, confirmedBy: b.received ? 'MANUAL' : null,
    }],
    manualReleases: [], lastSyncAt: new Date().toISOString(), syncError: false, syncErrorCode: null,
    nextRefreshAvailableAt: null,
  };
}

const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

async function setAuthCookies(context: BrowserContext) {
  await context.addCookies([
    { name: 'auth-session', value: JSON.stringify({ userId: assoUser.id, role: 'ASSOCIATION' }), domain: 'localhost', path: '/' },
    { name: 'cl-refresh', value: 'e2e-refresh-token', domain: 'localhost', path: '/' },
  ]);
}

async function mockApi(page: Page, backend: FakeBackend) {
  await page.route(`${API_BASE}/api/**`, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    backend.calls.push(`${method} ${path}`);

    if (path === '/api/auth/refresh') {
      return json(route, { accessToken: 'test-access-token', refreshToken: 'test-refresh-token', user: assoUser });
    }
    if (path === `/api/association/campaigns/${CAMPAIGN_ID}`) return json(route, campaign);
    if (path === `/api/association/campaigns/${CAMPAIGN_ID}/funds` || path === `/api/association/campaigns/${CAMPAIGN_ID}/funds/refresh`) {
      return json(route, funds(backend));
    }
    if (path === '/api/association/cash-outs/60001/receipt' && method === 'POST') {
      backend.received = true;
      return json(route, { idCashOut: 60001, netTotal: 1001, donations: [] });
    }
    if (path === '/api/association/payees') return json(route, [payee]);
    if (path === `/api/campaigns/${CAMPAIGN_ID}/payments`) {
      return method === 'GET'
        ? json(route, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 })
        : json(route, { id: 'payout-1', status: 'PENDING' });
    }
    if (path === `/api/campaigns/${CAMPAIGN_ID}/payments/summary`) {
      return json(route, { confirmedAmount: 0, confirmedCount: 0, pendingAmount: 0, txTotal: 0, txConfirmed: 0, availableBalance: backend.received ? 30 : 0, paymentsEnabled: true });
    }
    if (path === `/api/campaigns/${CAMPAIGN_ID}/payments/blocking-reasons`) return json(route, { reasons: [] });

    // Anything else the dashboard shell reads: an empty but valid answer.
    return json(route, method === 'GET' ? {} : null);
  });
}

async function openPaymentsTab(page: Page, context: BrowserContext, backend: FakeBackend) {
  await setAuthCookies(context);
  await mockApi(page, backend);
  await page.goto(`/fr/dashboard/association/campaigns/${CAMPAIGN_ID}?tab=payments`);
  await expect(page.getByText('Où en sont vos dons')).toBeVisible({ timeout: 10000 });
}

test.describe('Funds availability (HelloAsso payouts)', () => {
  test('confirming a transfer makes its donations usable, then a payment is possible', async ({ page, context }) => {
    const backend: FakeBackend = { received: false, calls: [] };
    await openPaymentsTab(page, context, backend);

    await expect(page.getByText('Aucun fonds disponible pour un paiement.')).toBeVisible();
    await expect(page.getByText('1 virement à confirmer')).toBeVisible();

    await page.getByRole('button', { name: /En route/ }).click();
    await page.getByRole('button', { name: "J'ai reçu ce virement" }).click();
    const dialog = page.getByRole('dialog', { name: 'Avez-vous reçu ce virement ?' });
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /Oui, je l'ai reçu/ }).click();

    await expect(page.getByText('Virement confirmé : les fonds sont disponibles')).toBeVisible();
    expect(backend.calls).toContain('POST /api/association/cash-outs/60001/receipt');
    await expect(page.getByText('Aucun fonds disponible pour un paiement.')).toBeHidden();

    const form = page.locator('.pay-form-grid .cm-card').first();
    await form.getByRole('combobox').nth(0).selectOption('60-mat');
    await form.getByRole('combobox').nth(1).selectOption('payee-1');
    await form.getByPlaceholder('0,00').fill('30');
    await form.locator('textarea').fill('Achat de graines et de terreau');
    await expect(page.locator('#pay-amt-hint')).toContainText('Disponible pour vos paiements');
    await expect(form.getByRole('button', { name: /Émettre le paiement/ })).toBeEnabled();
  });

  test('keyboard only: open "En route", reach the confirm button, validate the dialog', async ({ page, context }) => {
    const backend: FakeBackend = { received: false, calls: [] };
    await openPaymentsTab(page, context, backend);

    const trigger = page.getByRole('button', { name: /En route/ });
    await trigger.focus();
    await expect(page.locator('#funds-tip-inTransit')).toBeVisible();

    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: "J'ai reçu ce virement" })).toBeFocused();
    await page.keyboard.press('Enter');

    const dialog = page.getByRole('dialog', { name: 'Avez-vous reçu ce virement ?' });
    await expect(dialog.getByRole('button', { name: 'Pas encore' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(dialog.getByRole('button', { name: /Oui, je l'ai reçu/ })).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page.getByText('Virement confirmé : les fonds sont disponibles')).toBeVisible();
    expect(backend.calls).toContain('POST /api/association/cash-outs/60001/receipt');
  });
});
