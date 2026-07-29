import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { AssociationStatusSync } from '../AssociationStatusSync';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';
import { MollieOnboardingStatus } from '@/types/mollie-connect';

const profileMock = vi.fn();
const mollieMock = vi.fn();

vi.mock('@/hooks/dashboard/useAssociationProfile', () => ({
  useAssociationProfile: () => profileMock(),
}));

vi.mock('@/hooks/mollie/useMollieKycStatus', () => ({
  useMollieKycStatus: () => mollieMock(),
}));

/** Minimal profile shape consumed by the sync component. */
function profile(verificationStatus: VerificationStatus, rejectionReason: string | null = null) {
  return { profile: { verificationStatus, verificationRejectionReason: rejectionReason } };
}

/** Mollie hook return, defaulting to "still loading / never connected". */
function mollie(overrides: Record<string, unknown> = {}) {
  return { connected: null, broken: null, onboardingStatus: null, dashboardUrl: null, ...overrides };
}

beforeEach(() => {
  useAccStatusStore.setState({
    verificationStatus: VerificationStatus.UNVERIFIED,
    bankStatus: BankSetupStatus.NOT_CONNECTED,
    rejectionReason: null,
    mollieDashboardUrl: null,
    hydrated: false,
    done: 0,
    total: 2,
    verified: false,
    bank: false,
  });
});

describe('AssociationStatusSync', () => {
  /**
   * Regression guard: the previous implementation only hydrated the store when BOTH the profile
   * and the Mollie status were resolved, so a verified association that never connected Mollie
   * kept the default `verified: false` and was shown as "pending verification".
   */
  it('hydrates the store from the profile alone when the Mollie status is null', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.VERIFIED));
    mollieMock.mockReturnValue(mollie());

    render(<AssociationStatusSync />);

    const state = useAccStatusStore.getState();
    expect(state.verificationStatus).toBe(VerificationStatus.VERIFIED);
    expect(state.verified).toBe(true);
    expect(state.bankStatus).toBe(BankSetupStatus.NOT_CONNECTED);
    expect(state.bank).toBe(false);
    expect(state.done).toBe(1);
    expect(state.hydrated).toBe(true);
  });

  it('does not touch the store while the profile is still loading', () => {
    profileMock.mockReturnValue({ profile: null });
    mollieMock.mockReturnValue(mollie({ connected: true, broken: false, onboardingStatus: MollieOnboardingStatus.COMPLETED }));

    render(<AssociationStatusSync />);

    expect(useAccStatusStore.getState().bankStatus).toBe(BankSetupStatus.NOT_CONNECTED);
    expect(useAccStatusStore.getState().done).toBe(0);
    // Consumers gate on `hydrated`, so nothing is displayed from these defaults.
    expect(useAccStatusStore.getState().hydrated).toBe(false);
  });

  it('derives the bank status and exposes the Mollie wizard URL', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.PENDING));
    mollieMock.mockReturnValue(mollie({
      connected: true,
      broken: false,
      onboardingStatus: MollieOnboardingStatus.NEEDS_DATA,
      dashboardUrl: 'https://my.mollie.com/dashboard/onboarding',
    }));

    render(<AssociationStatusSync />);

    const state = useAccStatusStore.getState();
    expect(state.bankStatus).toBe(BankSetupStatus.NEEDS_DATA);
    expect(state.mollieDashboardUrl).toBe('https://my.mollie.com/dashboard/onboarding');
    expect(state.done).toBe(0);
  });

  it('propagates the rejection reason and marks both steps done when everything is complete', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.REJECTED, 'Statuts illisibles'));
    mollieMock.mockReturnValue(mollie({ connected: true, broken: false, onboardingStatus: MollieOnboardingStatus.COMPLETED }));

    render(<AssociationStatusSync />);

    const state = useAccStatusStore.getState();
    expect(state.rejectionReason).toBe('Statuts illisibles');
    expect(state.bank).toBe(true);
    expect(state.verified).toBe(false);
    expect(state.done).toBe(1);
  });
});
