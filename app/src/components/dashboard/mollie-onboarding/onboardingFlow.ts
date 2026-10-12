import { MollieOnboardingStatus } from '@/types/mollie-connect';

/**
 * Screens of the "Bank account" onboarding flow, numbered as in the validated mockup
 * (`app/minisite/onboarding-mollie.html`). Screens 0-2 ("Préparer") are local navigation; screens
 * 3-6 are delivered in sprint S4.
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
 * What the bank tab renders, derived from the server-side Mollie status.
 *
 * `PREPARE` is the new guided flow (stepper + screens 0-2). Every other value keeps the rendering
 * the tab had before sprint 20, until S4 replaces it with screens 3-6 and the dedicated states.
 */
export const FlowView = {
  LOADING: 'LOADING',
  ACTIVE: 'ACTIVE',
  BROKEN: 'BROKEN',
  IN_REVIEW: 'IN_REVIEW',
  PREPARE: 'PREPARE',
  INTERRUPTED: 'INTERRUPTED',
  PENDING: 'PENDING',
  /** Connected but neither NEEDS_DATA, IN_REVIEW nor collecting (e.g. COMPLETED without payments): legacy branch, S4. */
  CONNECT_FALLBACK: 'CONNECT_FALLBACK',
} as const;
export type FlowView = typeof FlowView[keyof typeof FlowView];

/** Server-side Mollie state the bank tab is rendered from (subset of `useMollieKycStatus`). */
export interface MollieFlowState {
  isLoading: boolean;
  connected: boolean | null;
  pending: boolean | null;
  broken: boolean | null;
  onboardingStatus: MollieOnboardingStatus | null;
  /** The page's `bankDone` (collection lock predicate), passed through unchanged. */
  bankDone: boolean;
  /** The OAuth popup was closed before completion (`mollieInterrupted` in the page). */
  interrupted: boolean;
}

/**
 * Picks the bank tab view, in the exact precedence order the tab used before sprint 20 — only the
 * not-connected and connected-NEEDS_DATA cases now lead to the guided flow.
 *
 * @param state server-side Mollie state plus the page's `bankDone` and interruption flag.
 * @return the view to render.
 */
export function resolveFlowView(state: MollieFlowState): FlowView {
  if (state.isLoading) return FlowView.LOADING;
  if (state.bankDone) return FlowView.ACTIVE;
  if (state.broken) return FlowView.BROKEN;
  if (state.connected && state.onboardingStatus === MollieOnboardingStatus.IN_REVIEW) return FlowView.IN_REVIEW;
  if (state.connected && state.onboardingStatus === MollieOnboardingStatus.NEEDS_DATA) return FlowView.PREPARE;
  if (state.interrupted) return FlowView.INTERRUPTED;
  if (state.pending) return FlowView.PENDING;
  return state.connected ? FlowView.CONNECT_FALLBACK : FlowView.PREPARE;
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
