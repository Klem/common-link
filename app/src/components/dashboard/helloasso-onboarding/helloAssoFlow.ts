import { HelloAssoLink, HelloAssoWebhookState, KeyCheck } from '@/types/helloasso';
import type { HelloAssoStatus } from '@/types/helloasso';

/**
 * Pure derivations of the HelloAsso journey of the "Encaissement des dons" tab (mockup
 * `app/minisite/onboarding-helloasso-v2.html`, script `canCollectDonations` / `currentScreen` / `renderStepper` /
 * `renderBankBadge` / `renderLink`). Everything here is computed from the server status (`helloasso` of
 * `GET /api/association/payment/status`) plus local UI choices; nothing unlocks donations — the server does.
 */

/** Screens of the journey. Numbers kept from the mockup (screens 1-2 were removed in its v3). */
export const HelloAssoScreen = {
  /** Two entry cards: "déjà un compte" / "pas encore de compte". */
  WELCOME: 0,
  /** Open the HelloAsso payment account (path "new"). */
  OPEN_ACCOUNT: 3,
  /** Paste the two codes and the notification URL, accept the mandate. */
  LINK: 4,
  /** Linked, HelloAsso has not verified the association yet. */
  REVIEW: 5,
  /** Linked and able to collect — or suspended after having been able to. */
  ACTIVE: 6,
} as const;
export type HelloAssoScreen = typeof HelloAssoScreen[keyof typeof HelloAssoScreen];

/** Entry path chosen on screen 0. */
export const HelloAssoPath = {
  /** No HelloAsso account yet: 0 → 3 → 4. */
  NEW: 'NEW',
  /** Already a HelloAsso account: 0 → 4, step 1 shown as "Déjà fait". */
  EXISTING: 'EXISTING',
} as const;
export type HelloAssoPath = typeof HelloAssoPath[keyof typeof HelloAssoPath];

/** What the tab shows instead of (or as) the flow. */
export const HelloAssoFlowView = {
  /** Status not read yet: skeleton. */
  LOADING: 'LOADING',
  /** Access refused by HelloAsso: "Votre accès HelloAsso est à renouveler" (unless re-pasting the key). */
  BROKEN: 'BROKEN',
  FLOW: 'FLOW',
} as const;
export type HelloAssoFlowView = typeof HelloAssoFlowView[keyof typeof HelloAssoFlowView];

/** Link details seen as "not linked" while the status carries none. */
const NOT_LINKED: Pick<HelloAssoStatus, 'link' | 'canCollect' | 'everCompliant' | 'webhook'> = {
  link: HelloAssoLink.NONE,
  canCollect: false,
  everCompliant: false,
  webhook: HelloAssoWebhookState.UNKNOWN,
};

type LinkState = Pick<HelloAssoStatus, 'link' | 'canCollect' | 'everCompliant'> | null | undefined;

/** Linked and HelloAsso accepts payments: donations are open. */
export function canCollectDonations(status: LinkState): boolean {
  return status?.link === HelloAssoLink.OK && status.canCollect;
}

/** Linked, HelloAsso no longer accepts payments although it did before. */
export function isSuspended(status: LinkState): boolean {
  return status?.link === HelloAssoLink.OK && !status.canCollect && status.everCompliant;
}

/**
 * Screen to show. Not linked: the screen the user navigated to (0, 3 or 4). Linked: 6 when collecting or suspended,
 * 5 otherwise. BROKEN is handled by [resolveFlowView]; while re-pasting the key it shows the user's screen (4).
 *
 * @param status HelloAsso link details, null/undefined = not linked.
 * @param chosen screen chosen by the user among 0, 3 and 4.
 */
export function currentScreen(status: LinkState, chosen: HelloAssoScreen): HelloAssoScreen {
  if (status?.link !== HelloAssoLink.OK) return chosen;
  return status.canCollect || status.everCompliant ? HelloAssoScreen.ACTIVE : HelloAssoScreen.REVIEW;
}

/**
 * Loading until the payment status is resolved; BROKEN replaces the flow unless the user is re-pasting the key.
 *
 * @param resolved whether the payment status has been read at least once.
 * @param status HelloAsso link details.
 * @param replacing whether the user chose "Recoller ma clé".
 */
export function resolveFlowView(resolved: boolean, status: LinkState, replacing: boolean): HelloAssoFlowView {
  if (!resolved) return HelloAssoFlowView.LOADING;
  if (status?.link === HelloAssoLink.BROKEN && !replacing) return HelloAssoFlowView.BROKEN;
  return HelloAssoFlowView.FLOW;
}

/** i18n keys of the four stepper steps, in order. */
const HELLOASSO_STEPS = ['account', 'link', 'review', 'active'] as const;

/** 1-based stepper step of each screen. */
const SCREEN_TO_STEP: Record<HelloAssoScreen, number> = {
  [HelloAssoScreen.WELCOME]: 1,
  [HelloAssoScreen.OPEN_ACCOUNT]: 1,
  [HelloAssoScreen.LINK]: 2,
  [HelloAssoScreen.REVIEW]: 3,
  [HelloAssoScreen.ACTIVE]: 4,
};

/** Visual state of one stepper step. */
export const StepState = {
  DONE: 'DONE',
  /** Step 1 on the "already an account" path: "Déjà fait". */
  SKIPPED: 'SKIPPED',
  CURRENT: 'CURRENT',
  UPCOMING: 'UPCOMING',
} as const;
export type StepState = typeof StepState[keyof typeof StepState];

/** One stepper step, ready to render. */
export interface StepperItem {
  key: typeof HELLOASSO_STEPS[number];
  /** 1-based number. */
  n: number;
  state: StepState;
  /** Current step shown in the warning colour. */
  warn: boolean;
}

/**
 * Stepper of the journey (mockup `renderStepper`): every step done once collecting; on the "existing" path step 1 is
 * skipped; the current step warns when suspended, or on screen 4 with invalid codes / another association.
 */
export function stepperItems(
  screen: HelloAssoScreen,
  path: HelloAssoPath | null,
  status: LinkState,
  keyCheck: KeyCheck,
): StepperItem[] {
  const step = SCREEN_TO_STEP[screen];
  const done = canCollectDonations(status);
  const keyProblem = screen === HelloAssoScreen.LINK && (keyCheck === KeyCheck.INVALID || keyCheck === KeyCheck.MISMATCH);
  return HELLOASSO_STEPS.map((key, index) => {
    const n = index + 1;
    const skipped = path === HelloAssoPath.EXISTING && n === 1 && !done;
    let state: StepState;
    if (skipped) state = StepState.SKIPPED;
    else if (done || n < step) state = StepState.DONE;
    else if (n === step) state = StepState.CURRENT;
    else state = StepState.UPCOMING;
    const warn = state === StepState.CURRENT && (isSuspended(status) || keyProblem);
    return { key, n, state, warn };
  });
}

/** Label of the "Encaissement des dons" tab badge for a HelloAsso association. */
export const HelloAssoTabBadge = {
  BROKEN: 'BROKEN',
  SUSPENDED: 'SUSPENDED',
  WEBHOOK_MISSING: 'WEBHOOK_MISSING',
  ACTIVE: 'ACTIVE',
  PENDING: 'PENDING',
  NOT_LINKED: 'NOT_LINKED',
} as const;
export type HelloAssoTabBadge = typeof HelloAssoTabBadge[keyof typeof HelloAssoTabBadge];

/** Colour of a tab badge (`set-tab-badge` modifier class; NEUTRAL = none). */
export const BadgeTone = {
  OK: 'ok',
  PENDING: 'pending',
  ERROR: 'err',
  NEUTRAL: '',
} as const;
export type BadgeTone = typeof BadgeTone[keyof typeof BadgeTone];

/**
 * Tab badge, by priority (mockup `renderBankBadge`, brief §2.1): access to renew · suspended · active with missing
 * notifications · active · linked and waiting for HelloAsso · not linked.
 */
export function helloAssoTabBadge(
  status: Pick<HelloAssoStatus, 'link' | 'canCollect' | 'everCompliant' | 'webhook'> | null | undefined,
): { label: HelloAssoTabBadge; tone: BadgeTone } {
  const s = status ?? NOT_LINKED;
  if (s.link === HelloAssoLink.BROKEN) return { label: HelloAssoTabBadge.BROKEN, tone: BadgeTone.ERROR };
  if (isSuspended(s)) return { label: HelloAssoTabBadge.SUSPENDED, tone: BadgeTone.ERROR };
  if (canCollectDonations(s) && s.webhook === HelloAssoWebhookState.MISSING) {
    return { label: HelloAssoTabBadge.WEBHOOK_MISSING, tone: BadgeTone.PENDING };
  }
  if (canCollectDonations(s)) return { label: HelloAssoTabBadge.ACTIVE, tone: BadgeTone.OK };
  if (s.link === HelloAssoLink.OK) return { label: HelloAssoTabBadge.PENDING, tone: BadgeTone.PENDING };
  return { label: HelloAssoTabBadge.NOT_LINKED, tone: BadgeTone.NEUTRAL };
}

/** What still blocks the "Relier mon compte" button (mockup `#link-hint`), in display order. */
export const LinkMissing = {
  KEYS: 'KEYS',
  ORG_CONFIRM: 'ORG_CONFIRM',
  WEBHOOK_ACK: 'WEBHOOK_ACK',
  MANDATE: 'MANDATE',
} as const;
export type LinkMissing = typeof LinkMissing[keyof typeof LinkMissing];

/** Local state of the "Relier" form that gates its submit button. */
export interface LinkFormState {
  keyCheck: KeyCheck;
  /** Server handle of the verified keys; required to link. */
  verifyId: string | null;
  orgConfirmed: boolean;
  webhookAck: boolean;
  mandateAccepted: boolean;
}

/**
 * Missing conditions of the link button: valid codes (with a verification handle), "C'est bien nous", notification
 * URL pasted, mandate. Mirrors the backend, which re-checks the handle and the mandate.
 */
export function linkMissing(form: LinkFormState): LinkMissing[] {
  const missing: LinkMissing[] = [];
  if (form.keyCheck !== KeyCheck.OK || !form.verifyId) missing.push(LinkMissing.KEYS);
  else if (!form.orgConfirmed) missing.push(LinkMissing.ORG_CONFIRM);
  if (!form.webhookAck) missing.push(LinkMissing.WEBHOOK_ACK);
  if (!form.mandateAccepted) missing.push(LinkMissing.MANDATE);
  return missing;
}

/** Whether the shared FAQ shows on [screen] (0, 5 and 6; hidden on the action screens 3 and 4). */
export function faqVisible(screen: HelloAssoScreen): boolean {
  return screen === HelloAssoScreen.WELCOME || screen === HelloAssoScreen.REVIEW || screen === HelloAssoScreen.ACTIVE;
}

/** Codes accepted by the backend: non-blank, at most 255 characters each (`HelloAssoVerifyRequest`). */
export const KEY_MAX_LENGTH = 255;

/** Whether both codes can be sent for verification (mirror of the backend `@NotBlank @Size(max = 255)`). */
export function keysReady(clientId: string, clientSecret: string): boolean {
  const id = clientId.trim();
  const secret = clientSecret.trim();
  return id.length > 0 && secret.length > 0 && id.length <= KEY_MAX_LENGTH && secret.length <= KEY_MAX_LENGTH;
}

/** Wait after the last keystroke before checking the codes (each check spends a HelloAsso authentication call). */
export const KEY_CHECK_DEBOUNCE_MS = 700;

/**
 * Version of the mandate wording shown on screen 4 (must equal the backend `HelloAssoLinkService.MANDATE_VERSION`;
 * the wording itself is `settings.helloasso.onboarding.link.mandate`). Bump both with any wording change.
 */
export const HELLOASSO_MANDATE_VERSION = 'helloasso-mandate-v2-2026-10-28';

/** HelloAsso administration space (slug unknown before linking, so no deep link on screen 4). */
export const HELLOASSO_ADMIN_URL = 'https://admin.helloasso.com';

/**
 * Association sign-up entry point of HelloAsso. TO CONFIRM with HelloAsso (brief §2.5, screen 3): the home page until
 * the exact sign-up URL is known.
 */
export const HELLOASSO_SIGNUP_URL = 'https://www.helloasso.com/';

/** Public register of authorised payment institutions, where HelloAsso's ACPR approval can be checked. */
export const REGAFI_URL = 'https://www.regafi.fr';

/** HelloAsso verification page of the linked organization (HelloAsso guideline, flow §10.5). */
export function verificationUrl(slug: string): string {
  return `${HELLOASSO_ADMIN_URL}/${encodeURIComponent(slug)}/verification`;
}

/**
 * Public fundraising page of the association, opened by the test donation: `/fr/lp/{widgetToken}` — locale pinned to
 * "fr" like the diffusion tab (`landing.js` is French-only). Null without a widget token or a destination campaign,
 * where the page would only show an error.
 */
export function fundraisingPageUrl(
  profile: { widgetToken: string | null; widgetDestinationCampaignId: string | null } | null | undefined,
): string | null {
  if (!profile?.widgetToken || !profile.widgetDestinationCampaignId) return null;
  return `/fr/lp/${encodeURIComponent(profile.widgetToken)}`;
}

/** Same page without the scheme, as displayed on screen 5. */
export function verificationUrlLabel(slug: string): string {
  return verificationUrl(slug).replace(/^https:\/\//, '');
}

/**
 * Video-call booking page of the help card ("Réserver une visio"), from `NEXT_PUBLIC_HELLOASSO_VISIO_URL` (inlined at
 * build time). Empty = the card and every "help" link are hidden. A function so tests can stub the variable.
 */
export function helloAssoVisioUrl(): string {
  return (process.env.NEXT_PUBLIC_HELLOASSO_VISIO_URL ?? '').trim();
}

/**
 * PLACEHOLDER — CEO decision pending: CommonLink's fees for associations collecting through HelloAsso (FAQ "Combien
 * ça coûte ?"). Shown with a visible "Décision en attente" badge. Must not ship to production: replace the wording,
 * then set this to false.
 */
export const HELLOASSO_FEES_DECISION_PENDING = true;

/**
 * Wall-clock time `HH:MM` of an ISO instant, in the user's time zone; null when absent or unreadable.
 *
 * @param iso ISO-8601 instant.
 * @param locale UI locale.
 */
export function formatClock(iso: string | null | undefined, locale: string): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' });
}

/**
 * Date `JJ/MM/AAAA` of an ISO instant, in the user's time zone; null when absent or unreadable.
 *
 * @param iso ISO-8601 instant.
 * @param locale UI locale.
 */
export function formatDay(iso: string | null | undefined, locale: string): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleDateString(locale, { day: '2-digit', month: '2-digit', year: 'numeric' });
}
