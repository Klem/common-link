import { test, expect, BrowserContext, Page, Route } from '@playwright/test';

/**
 * HelloAsso journey of the "Encaissement des dons" tab (lot G, S7-09). The API is simulated with `page.route`, like the
 * other specs of this folder (decision 03/11/2026): a small in-memory backend keeps the association's HelloAsso link,
 * so linking, eligibility and disconnection change what `GET /api/association/payment/status` answers. The backend's
 * own mock mode (`HELLOASSO_MOCK`) is covered API-side by `HelloAssoEndToEndMockTest`.
 */

const API_BASE = 'http://localhost:8080';
const NOTIFICATION_URL = `${API_BASE}/api/public/webhooks/helloasso/tok-e2e`;

const assoUser = {
  id: 'e2e-asso-ha',
  email: 'asso@example.com',
  role: 'ASSOCIATION',
  displayName: 'Les Amis du Jardin',
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: new Date().toISOString(),
};

const profile = {
  id: 'assoc-e2e-ha',
  name: 'Les Amis du Jardin',
  verificationStatus: 'VERIFIED',
  verificationRejectionReason: null,
  contactEmail: 'asso@example.com',
  contactName: 'Karim Benali',
  widgetToken: 'clk_e2e',
  widgetDestinationCampaignId: 'camp-e2e',
};

const Link = { NONE: 'NONE', OK: 'OK', BROKEN: 'BROKEN' } as const;
type Link = typeof Link[keyof typeof Link];

/** What `credentials/verify` answers. */
const Keys = { OK: 'OK', INVALID: 'INVALID', OTHER: 'OTHER' } as const;
type Keys = typeof Keys[keyof typeof Keys];

/** In-memory HelloAsso side of the backend. */
interface FakeBackend {
  link: Link;
  canCollect: boolean;
  everCompliant: boolean;
  /** What `credentials/verify` answers. */
  keys: Keys;
  /** Whether linking an already-verified organization opens collection at once (path "existing"). */
  eligibleOnLink: boolean;
  calls: string[];
}

async function setAuthCookies(context: BrowserContext) {
  await context.addCookies([
    { name: 'auth-session', value: JSON.stringify({ userId: assoUser.id, role: 'ASSOCIATION' }), domain: 'localhost', path: '/' },
    { name: 'cl-refresh', value: 'e2e-refresh-token', domain: 'localhost', path: '/' },
  ]);
}

const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

function paymentStatus(b: FakeBackend) {
  const linked = b.link === Link.OK;
  const now = new Date().toISOString();
  return {
    provider: 'HELLOASSO',
    status: b.link === Link.BROKEN ? 'BROKEN' : !linked ? 'NOT_CONNECTED' : b.canCollect ? 'COMPLETED' : b.everCompliant ? 'NEEDS_DATA' : 'IN_REVIEW',
    actionUrl: null,
    warnings: [],
    helloasso: {
      link: b.link,
      notificationUrl: NOTIFICATION_URL,
      organizationSlug: linked ? 'les-amis-du-jardin' : null,
      canCollect: b.canCollect,
      everCompliant: b.everCompliant,
      lastEligibilityCheckAt: linked ? now : null,
      webhook: 'UNKNOWN',
      clientIdMasked: linked ? '9fdc…3715' : null,
      linkedAt: linked ? now : null,
      linkedBy: linked ? assoUser.email : null,
      secretWipedAt: linked ? now : null,
      lastRefreshAt: null,
    },
  };
}

/** Routes every API call the profile page makes; HelloAsso calls go through [backend]. */
async function mockApi(page: Page, backend: FakeBackend) {
  await page.route(`${API_BASE}/api/**`, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    backend.calls.push(`${method} ${path}`);

    if (path === '/api/auth/refresh') {
      return json(route, { accessToken: 'test-access-token', refreshToken: 'test-refresh-token', user: assoUser });
    }
    if (path === '/api/association/me') return json(route, profile);
    if (path === '/api/association/mandate') return json(route, { signed: true, mandateDocs: [], blocked: false });
    if (path === '/api/mollie/connect/status') return json(route, { connected: false });
    if (path === '/api/association/payment/status') return json(route, paymentStatus(backend));
    if (path === '/api/association/documents/kyb') return json(route, []);

    if (path === '/api/association/helloasso/notification-url') return json(route, { notificationUrl: NOTIFICATION_URL });
    if (path === '/api/association/helloasso/onboarding-started') return route.fulfill({ status: 204 });
    if (path === '/api/association/helloasso/credentials/verify') {
      if (backend.keys === Keys.INVALID) return json(route, { code: 'HELLOASSO_INVALID_KEYS' }, 422);
      if (backend.keys === Keys.OTHER) {
        return json(route, { verifyId: null, orgName: 'Jardins Solidaires', slug: 'jardins-solidaires', matchesKyb: false, canCollect: true });
      }
      return json(route, { verifyId: 'verify-e2e', orgName: profile.name, slug: 'les-amis-du-jardin', matchesKyb: true, canCollect: backend.eligibleOnLink });
    }
    if (path === '/api/association/helloasso/connection' && method === 'POST') {
      backend.link = Link.OK;
      backend.canCollect = backend.eligibleOnLink;
      backend.everCompliant = backend.eligibleOnLink;
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/association/helloasso/connection' && method === 'DELETE') {
      backend.link = Link.NONE;
      backend.canCollect = false;
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/association/helloasso/eligibility/refresh') return route.fulfill({ status: 204 });

    // Anything else the dashboard shell reads: an empty but valid answer.
    return json(route, method === 'GET' ? {} : null);
  });
}

function newBackend(overrides: Partial<FakeBackend> = {}): FakeBackend {
  return { link: Link.NONE, canCollect: false, everCompliant: false, keys: Keys.OK, eligibleOnLink: false, calls: [], ...overrides };
}

async function openBankTab(page: Page, context: BrowserContext, backend: FakeBackend) {
  await setAuthCookies(context);
  await mockApi(page, backend);
  await page.goto('/fr/dashboard/association/profile?tab=bank');
}

async function pasteCodes(page: Page) {
  await page.getByLabel(/Identifiant client/).fill('9fdc22226bf24ff99b875f4a7c503715');
  await page.getByLabel(/Clé secrète/).fill('q2Lx9FvR7mN4tK8wZ1pYc6Hs3Jd0Gb5Ue=');
}

async function completeLink(page: Page) {
  await page.getByRole('checkbox', { name: "C'est bien nous" }).check();
  await page.getByRole('checkbox', { name: "C'est collé et enregistré chez HelloAsso" }).check();
  await page.getByRole('checkbox', { name: /Au nom de l'association/ }).check();
  await page.getByRole('button', { name: 'Relier mon compte' }).click();
}

test.describe('HelloAsso onboarding (mock API)', () => {
  test('path "new": open the account, link, wait for HelloAsso, then active', async ({ page, context }) => {
    const backend = newBackend();
    await openBankTab(page, context, backend);

    await page.getByRole('button', { name: 'Commencer' }).click();
    await expect(page.getByRole('heading', { name: "Ouvrir le compte de paiement de l'association" })).toBeVisible();
    expect(backend.calls).toContain('POST /api/association/helloasso/onboarding-started');
    await expect(page.getByRole('link', { name: 'Ouvrir HelloAsso ↗' })).toHaveAttribute('target', '_blank');

    await page.getByRole('button', { name: 'Notre compte est ouvert, continuer' }).click();
    await expect(page.getByText(NOTIFICATION_URL)).toBeVisible();
    await pasteCodes(page);
    await expect(page.getByText("Correspond à l'association vérifiée sur CommonLink")).toBeVisible({ timeout: 5000 });
    await completeLink(page);

    // Linked, HelloAsso has not verified the association yet.
    await expect(page.getByRole('heading', { name: 'Vérification de votre association par HelloAsso' })).toBeVisible();
    await expect(page.getByText('Compte relié. Votre clé secrète a été effacée de nos serveurs.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ouvrir ma page de vérification ↗' })).toHaveAttribute(
      'href',
      'https://admin.helloasso.com/les-amis-du-jardin/verification',
    );

    // HelloAsso verifies the association; "Vérifier mon dossier" re-reads it.
    backend.canCollect = true;
    backend.everCompliant = true;
    await page.getByRole('button', { name: 'Vérifier mon dossier' }).click();
    await expect(page.getByRole('heading', { name: 'Votre association peut recevoir des dons' })).toBeVisible();
    await expect(page.locator('.set-tab-badge.ok')).toHaveText('Actif');
  });

  test('path "existing": step 1 already done, an eligible organization is active right after linking', async ({ page, context }) => {
    const backend = newBackend({ eligibleOnLink: true });
    await openBankTab(page, context, backend);

    await page.getByRole('button', { name: 'Relier notre compte' }).click();
    await expect(page.getByText('Déjà fait')).toBeVisible();
    await pasteCodes(page);
    await completeLink(page);

    await expect(page.getByRole('heading', { name: 'Votre association peut recevoir des dons' })).toBeVisible();
    expect(backend.calls.filter((c) => c === 'POST /api/association/helloasso/connection')).toHaveLength(1);
  });

  test('invalid keys: refused, nothing linked', async ({ page, context }) => {
    const backend = newBackend({ keys: Keys.INVALID });
    await openBankTab(page, context, backend);

    await page.getByRole('button', { name: 'Relier notre compte' }).click();
    await pasteCodes(page);

    await expect(page.getByRole('alert').filter({ hasText: 'HelloAsso ne reconnaît pas ces codes.' })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('button', { name: 'Relier mon compte' })).toBeDisabled();
    expect(backend.calls).not.toContain('POST /api/association/helloasso/connection');
  });

  test('another association: refused, nothing is ever sent to /connection', async ({ page, context }) => {
    const backend = newBackend({ keys: Keys.OTHER });
    await openBankTab(page, context, backend);

    await page.getByRole('button', { name: 'Relier notre compte' }).click();
    await pasteCodes(page);

    await expect(page.getByText('Ces codes appartiennent à « Jardins Solidaires ».')).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('checkbox', { name: "C'est bien nous" })).toHaveCount(0);
    await page.getByRole('checkbox', { name: "C'est collé et enregistré chez HelloAsso" }).check();
    await page.getByRole('checkbox', { name: /Au nom de l'association/ }).check();
    await expect(page.getByRole('button', { name: 'Relier mon compte' })).toBeDisabled();
    expect(backend.calls).not.toContain('POST /api/association/helloasso/connection');
  });

  test('disconnect: warning modal, then back to the welcome screen', async ({ page, context }) => {
    const backend = newBackend({ link: Link.OK, canCollect: true, everCompliant: true });
    await openBankTab(page, context, backend);

    const trigger = page.getByRole('button', { name: 'Se déconnecter de HelloAsso' });
    await trigger.click();
    const dialog = page.getByRole('alertdialog', { name: 'Se déconnecter de HelloAsso ?' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Annuler' })).toBeFocused();

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();

    await trigger.click();
    await dialog.getByRole('button', { name: 'Oui, me déconnecter' }).click();
    await expect(page.getByText('HelloAsso déconnecté. Nous ne gardons plus aucun accès.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Relier notre compte' })).toBeVisible();
    expect(backend.calls).toContain('DELETE /api/association/helloasso/connection');
  });
});
