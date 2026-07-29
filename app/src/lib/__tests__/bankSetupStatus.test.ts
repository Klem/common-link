import { describe, it, expect } from 'vitest';
import { deriveBankSetupStatus, BankSetupStatus } from '../bankSetupStatus';
import { MollieOnboardingStatus } from '@/types/mollie-connect';

describe('deriveBankSetupStatus', () => {
  it('returns NOT_CONNECTED while the Mollie status is still loading', () => {
    expect(deriveBankSetupStatus({ connected: null, broken: null, onboardingStatus: null }))
      .toBe(BankSetupStatus.NOT_CONNECTED);
  });

  it('returns NOT_CONNECTED when Mollie was never connected', () => {
    expect(deriveBankSetupStatus({ connected: false, broken: false, onboardingStatus: null }))
      .toBe(BankSetupStatus.NOT_CONNECTED);
  });

  it('maps each Mollie onboarding status when connected', () => {
    const cases = [
      [MollieOnboardingStatus.NEEDS_DATA, BankSetupStatus.NEEDS_DATA],
      [MollieOnboardingStatus.IN_REVIEW, BankSetupStatus.IN_REVIEW],
      [MollieOnboardingStatus.COMPLETED, BankSetupStatus.COMPLETED],
    ] as const;
    for (const [onboardingStatus, expected] of cases) {
      expect(deriveBankSetupStatus({ connected: true, broken: false, onboardingStatus })).toBe(expected);
    }
  });

  it('returns BROKEN even when a stale onboarding status says COMPLETED', () => {
    expect(deriveBankSetupStatus({
      connected: true,
      broken: true,
      onboardingStatus: MollieOnboardingStatus.COMPLETED,
    })).toBe(BankSetupStatus.BROKEN);
  });

  it('returns NOT_CONNECTED when a status exists but the link is not marked connected', () => {
    expect(deriveBankSetupStatus({
      connected: false,
      broken: false,
      onboardingStatus: MollieOnboardingStatus.IN_REVIEW,
    })).toBe(BankSetupStatus.NOT_CONNECTED);
  });
});
