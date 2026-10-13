import { describe, it, expect } from 'vitest';
import {
  BankTabBadge,
  FlowNotice,
  FlowView,
  OnboardingScreen,
  SCREEN_TO_STEP,
  bankTabBadge,
  flowNotice,
  resolveFlowView,
} from '../onboardingFlow';
import type { MollieFlowState } from '../onboardingFlow';
import { MollieOnboardingStatus } from '@/types/mollie-connect';

const base: MollieFlowState = {
  isLoading: false,
  connected: false,
  pending: false,
  broken: false,
  onboardingStatus: null,
  canReceivePayments: null,
  complementRequested: null,
  bankDone: false,
  interrupted: false,
};
const connected = (status: MollieOnboardingStatus, extra: Partial<MollieFlowState> = {}): MollieFlowState => ({
  ...base,
  connected: true,
  onboardingStatus: status,
  canReceivePayments: false,
  complementRequested: false,
  ...extra,
});

describe('resolveFlowView', () => {
  it('shows the local flow when no Mollie onboarding exists yet, or when it needs data', () => {
    expect(resolveFlowView(base)).toBe(FlowView.FLOW);
    expect(resolveFlowView(connected(MollieOnboardingStatus.NEEDS_DATA))).toBe(FlowView.FLOW);
  });

  it('shows the review screen in review, and when Mollie asked for a complement', () => {
    expect(resolveFlowView(connected(MollieOnboardingStatus.IN_REVIEW))).toBe(FlowView.REVIEW);
    expect(resolveFlowView(connected(MollieOnboardingStatus.NEEDS_DATA, { complementRequested: true }))).toBe(FlowView.REVIEW);
  });

  it('shows screen 6 when collecting, and its no-payments variant for COMPLETED without payments', () => {
    expect(resolveFlowView({ ...connected(MollieOnboardingStatus.COMPLETED, { canReceivePayments: true }), bankDone: true })).toBe(
      FlowView.ACTIVE,
    );
    expect(resolveFlowView(connected(MollieOnboardingStatus.COMPLETED))).toBe(FlowView.ACTIVE_NO_PAYMENTS);
  });

  it('gives precedence to loading, then bankDone, then a broken connection', () => {
    expect(resolveFlowView({ ...base, isLoading: true, bankDone: true })).toBe(FlowView.LOADING);
    expect(resolveFlowView({ ...base, bankDone: true, broken: true })).toBe(FlowView.ACTIVE);
    expect(resolveFlowView(connected(MollieOnboardingStatus.COMPLETED, { broken: true }))).toBe(FlowView.BROKEN);
  });
});

describe('flowNotice', () => {
  it('flags an interrupted or pending connection only before any connection exists', () => {
    expect(flowNotice(base)).toBeNull();
    expect(flowNotice({ ...base, interrupted: true, pending: true })).toBe(FlowNotice.INTERRUPTED);
    expect(flowNotice({ ...base, pending: true })).toBe(FlowNotice.PENDING);
    expect(flowNotice(connected(MollieOnboardingStatus.NEEDS_DATA, { interrupted: true }))).toBeNull();
  });
});

describe('SCREEN_TO_STEP', () => {
  it('maps screens 0-2 to step 1 and screens 3-6 to steps 2-5 (mockup)', () => {
    expect([0, 1, 2, 3, 4, 5, 6].map((s) => SCREEN_TO_STEP[s as OnboardingScreen])).toEqual([1, 1, 1, 2, 3, 4, 5]);
  });
});

describe('bankTabBadge', () => {
  const badge = { connected: true, broken: false, onboardingStatus: null, canReceivePayments: false, bankDone: false };

  it('says "active" exactly when bankDone', () => {
    expect(bankTabBadge({ ...badge, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true, bankDone: true })).toBe(
      BankTabBadge.ACTIVE,
    );
  });

  it('labels each onboarding state', () => {
    expect(bankTabBadge({ ...badge, connected: false })).toBe(BankTabBadge.NOT_CONNECTED);
    expect(bankTabBadge({ ...badge, broken: true })).toBe(BankTabBadge.RECONNECT);
    expect(bankTabBadge({ ...badge, onboardingStatus: MollieOnboardingStatus.NEEDS_DATA })).toBe(BankTabBadge.TO_COMPLETE);
    expect(bankTabBadge({ ...badge, onboardingStatus: MollieOnboardingStatus.IN_REVIEW })).toBe(BankTabBadge.IN_REVIEW);
    expect(bankTabBadge({ ...badge, onboardingStatus: MollieOnboardingStatus.COMPLETED })).toBe(
      BankTabBadge.PAYMENTS_NOT_ENABLED,
    );
  });
});
