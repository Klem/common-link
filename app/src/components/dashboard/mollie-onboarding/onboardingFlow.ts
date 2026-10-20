import { MollieOnboardingStatus } from '@/types/mollie-connect';

/**
 * Screens of the "Bank account" onboarding flow, numbered as in the validated mockup
 * (`app/minisite/onboarding-mollie.html`). Screens 0-4 are local navigation (while no Mollie
 * onboarding exists or it needs data); screens 5 and 6 are driven by the server status.
 */
export const OnboardingScreen = {
  WELCOME: 0,
  STRUCTURE: 1,
  KIT: 2,
  FILL: 3,
  TEST_PAYMENT: 4,
  REVIEW: 5,
  ACTIVE: 6,
} as const;
export type OnboardingScreen = typeof OnboardingScreen[keyof typeof OnboardingScreen];

/** Local screens (0-4) a user can navigate to; the others are driven by the server status. */
const LOCAL_SCREENS: readonly OnboardingScreen[] = [
  OnboardingScreen.WELCOME,
  OnboardingScreen.STRUCTURE,
  OnboardingScreen.KIT,
  OnboardingScreen.FILL,
  OnboardingScreen.TEST_PAYMENT,
];

const screenStorageKey = (associationId: string) => `cl.mollieOnboarding.screen.${associationId}`;

/**
 * Last local screen visited by an association, so coming back to the bank tab (tab switch or page
 * reload) does not replay the earlier steps. Storage may be unavailable (private window, blocked
 * site data): every failure falls back to the welcome screen.
 *
 * @param associationId profile id; without it nothing is persisted.
 * @return the stored local screen, or [OnboardingScreen.WELCOME].
 */
export function readStoredScreen(associationId: string | undefined): OnboardingScreen {
  if (!associationId || typeof window === 'undefined') return OnboardingScreen.WELCOME;
  try {
    const stored = Number(window.localStorage.getItem(screenStorageKey(associationId)));
    return LOCAL_SCREENS.find((screen) => screen === stored) ?? OnboardingScreen.WELCOME;
  } catch {
    return OnboardingScreen.WELCOME;
  }
}

/**
 * Remembers the last local screen visited (see [readStoredScreen]); no-op without an id or storage.
 *
 * @param associationId profile id.
 * @param screen the screen to remember.
 */
export function storeScreen(associationId: string | undefined, screen: OnboardingScreen): void {
  if (!associationId || typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(screenStorageKey(associationId), String(screen));
  } catch {
    // Persistence is a convenience only.
  }
}

/** Stepper steps, in display order; each value is the i18n key under `settings.mollie.onboarding.stepper.steps`. */
export const ONBOARDING_STEPS = ['prepare', 'fill', 'testPayment', 'review', 'active'] as const;

/** 1-based stepper step for each screen (mockup `SCREEN_TO_STEP`: screens 0-2 → 1, 3 → 2, 4 → 3, 5 → 4, 6 → 5). */
export const SCREEN_TO_STEP: Record<OnboardingScreen, number> = {
  [OnboardingScreen.WELCOME]: 1,
  [OnboardingScreen.STRUCTURE]: 1,
  [OnboardingScreen.KIT]: 1,
  [OnboardingScreen.FILL]: 2,
  [OnboardingScreen.TEST_PAYMENT]: 3,
  [OnboardingScreen.REVIEW]: 4,
  [OnboardingScreen.ACTIVE]: 5,
};

/**
 * Public booking page of the CommonLink "visio accompagnée" (decision R4, 12/10/2026). Single source
 * for every "book a video call" entry point of the bank tab (FAQ 6 of screen 0, help card).
 * Always opened with `target="_blank" rel="noopener noreferrer"`.
 */
export const VISIO_BOOKING_URL = 'https://calendar.proton.me/bookings/guest#HwvqIxtLuDJOaPIrg1-JrTXq9Cj-N2Y79_u2UXr_MiQ=';

/** Interval of the bank tab's background re-read of `GET /api/mollie/connect/status`, in milliseconds. */
export const STATUS_POLL_INTERVAL_MS = 60_000;

/**
 * What the bank tab renders, derived from the server-side Mollie status (mockup `render` /
 * `currentScreen`).
 *
 * - `FLOW`: screens 0-4, navigated locally (no Mollie onboarding yet, or NEEDS_DATA).
 * - `REVIEW`: screen 5 (IN_REVIEW, or NEEDS_DATA after a review: complement requested).
 * - `ACTIVE`: screen 6, collecting (`bankDone`).
 * - `ACTIVE_NO_PAYMENTS`: screen 6, COMPLETED but payments not enabled yet — never "Connect Mollie".
 */
export const FlowView = {
  LOADING: 'LOADING',
  BROKEN: 'BROKEN',
  FLOW: 'FLOW',
  REVIEW: 'REVIEW',
  ACTIVE: 'ACTIVE',
  ACTIVE_NO_PAYMENTS: 'ACTIVE_NO_PAYMENTS',
} as const;
export type FlowView = typeof FlowView[keyof typeof FlowView];

/**
 * Notice shown above the local screens when no Mollie connection exists yet but a connection
 * attempt was made: the OAuth popup was closed before completion, or an authorization is still
 * pending server-side. Each value is the i18n key under `settings.mollie.onboarding.notices`.
 */
export const FlowNotice = {
  INTERRUPTED: 'interrupted',
  PENDING: 'pending',
} as const;
export type FlowNotice = typeof FlowNotice[keyof typeof FlowNotice];

/** Server-side Mollie state the bank tab is rendered from (subset of `useMollieKycStatus`). */
export interface MollieFlowState {
  isLoading: boolean;
  connected: boolean | null;
  pending: boolean | null;
  broken: boolean | null;
  onboardingStatus: MollieOnboardingStatus | null;
  canReceivePayments: boolean | null;
  complementRequested: boolean | null;
  /** The page's `bankDone` (collection lock predicate), passed through unchanged. */
  bankDone: boolean;
  /** The OAuth popup was closed before completion (`mollieInterrupted` in the page). */
  interrupted: boolean;
}

/**
 * Picks the bank tab view. Precedence: loading, collecting (`bankDone`), broken connection,
 * COMPLETED without payments, in review / complement requested, then the local flow.
 *
 * @param state server-side Mollie state plus the page's `bankDone` and interruption flag.
 * @return the view to render.
 */
export function resolveFlowView(state: MollieFlowState): FlowView {
  if (state.isLoading) return FlowView.LOADING;
  if (state.bankDone) return FlowView.ACTIVE;
  if (state.broken) return FlowView.BROKEN;
  if (state.connected && state.onboardingStatus === MollieOnboardingStatus.COMPLETED) return FlowView.ACTIVE_NO_PAYMENTS;
  if (state.connected && state.onboardingStatus === MollieOnboardingStatus.IN_REVIEW) return FlowView.REVIEW;
  if (state.connected && state.onboardingStatus === MollieOnboardingStatus.NEEDS_DATA && state.complementRequested) {
    return FlowView.REVIEW;
  }
  return FlowView.FLOW;
}

/**
 * Notice to show on the local flow (see [FlowNotice]); only before any Mollie connection exists.
 *
 * @param state same state as [resolveFlowView].
 * @return the notice, or null.
 */
export function flowNotice(state: MollieFlowState): FlowNotice | null {
  if (state.connected) return null;
  if (state.interrupted) return FlowNotice.INTERRUPTED;
  if (state.pending) return FlowNotice.PENDING;
  return null;
}

/**
 * Label of the "bank" tab badge; each value is the i18n key under
 * `dashboard.association.profile.tabs.bankBadge`.
 */
export const BankTabBadge = {
  NOT_CONNECTED: 'notConnected',
  TO_COMPLETE: 'toComplete',
  IN_REVIEW: 'inReview',
  PAYMENTS_NOT_ENABLED: 'paymentsNotEnabled',
  RECONNECT: 'reconnect',
  ACTIVE: 'active',
} as const;
export type BankTabBadge = typeof BankTabBadge[keyof typeof BankTabBadge];

/** Inputs of [bankTabBadge] — the same state the tab itself is rendered from. */
export interface BankTabBadgeState {
  connected: boolean | null;
  broken: boolean | null;
  onboardingStatus: MollieOnboardingStatus | null;
  canReceivePayments: boolean | null;
  bankDone: boolean;
}

/**
 * Label of the "bank" tab badge (mockup `renderBankBadge`, question Q7).
 *
 * Only the label varies: the badge's `ok` class stays strictly `bankDone`, so the tab lock and the
 * badge never diverge. "Not connected" has no mockup equivalent (the mockup always assumes an
 * existing Mollie onboarding) and keeps the label the tab had before.
 *
 * @param state the tab's Mollie state and `bankDone`.
 * @return the badge label key.
 */
export function bankTabBadge(state: BankTabBadgeState): BankTabBadge {
  if (state.bankDone) return BankTabBadge.ACTIVE;
  if (state.broken) return BankTabBadge.RECONNECT;
  if (!state.connected) return BankTabBadge.NOT_CONNECTED;
  if (state.onboardingStatus === MollieOnboardingStatus.IN_REVIEW) return BankTabBadge.IN_REVIEW;
  if (state.onboardingStatus === MollieOnboardingStatus.COMPLETED && !state.canReceivePayments) {
    return BankTabBadge.PAYMENTS_NOT_ENABLED;
  }
  return BankTabBadge.TO_COMPLETE;
}
