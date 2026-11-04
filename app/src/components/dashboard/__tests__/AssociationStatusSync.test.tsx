import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { AssociationStatusSync } from '../AssociationStatusSync';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';
import { PaymentProviderId } from '@/types/paymentProvider';

const profileMock = vi.fn();
const paymentMock = vi.fn();

vi.mock('@/hooks/dashboard/useAssociationProfile', () => ({
  useAssociationProfile: () => profileMock(),
}));

vi.mock('@/hooks/payment/usePaymentStatus', () => ({
  usePaymentStatus: () => paymentMock(),
}));

/** Minimal profile shape consumed by the sync component. */
function profile(verificationStatus: VerificationStatus, rejectionReason: string | null = null) {
  return { profile: { verificationStatus, verificationRejectionReason: rejectionReason } };
}

/** Payment-status hook return, defaulting to "request settled / Mollie, never connected". */
function payment(overrides: Record<string, unknown> = {}) {
  return {
    provider: PaymentProviderId.MOLLIE,
    status: BankSetupStatus.NOT_CONNECTED,
    actionUrl: null,
    warnings: [],
    helloasso: null,
    isLoading: false,
    error: null,
    refresh: () => Promise.resolve(),
    ...overrides,
  };
}

beforeEach(() => {
  useAccStatusStore.setState({
    verificationStatus: VerificationStatus.UNVERIFIED,
    bankStatus: BankSetupStatus.NOT_CONNECTED,
    rejectionReason: null,
    bankActionUrl: null,
    bankResolved: false,
    paymentProvider: null,
    hydrated: false,
    done: 0,
    total: 2,
    verified: false,
    bank: false,
  });
});

describe('AssociationStatusSync', () => {
  /**
   * Regression guard: an earlier implementation only hydrated the store when BOTH the profile and
   * the bank status were resolved, so a verified association that never connected a provider kept
   * the default `verified: false` and was shown as "pending verification".
   */
  it('hydrates the store from the profile alone, whatever the payment status', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.VERIFIED));
    paymentMock.mockReturnValue(payment());

    render(<AssociationStatusSync />);

    const state = useAccStatusStore.getState();
    expect(state.verificationStatus).toBe(VerificationStatus.VERIFIED);
    expect(state.verified).toBe(true);
    expect(state.bankStatus).toBe(BankSetupStatus.NOT_CONNECTED);
    expect(state.bank).toBe(false);
    expect(state.done).toBe(1);
    expect(state.hydrated).toBe(true);
  });

  /**
   * Regression guard for the "compte bancaire non connecté" bug: while the status request is in
   * flight, the bank status defaults to NOT_CONNECTED, which is indistinguishable from a real
   * absence of connection. `bankResolved` carries that distinction so consumers can wait instead
   * of claiming a fully onboarded association has no bank account.
   */
  it('reports the payment status as unresolved while it is still loading', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.VERIFIED));
    paymentMock.mockReturnValue(payment({ provider: null, status: null, isLoading: true }));

    render(<AssociationStatusSync />);

    const state = useAccStatusStore.getState();
    expect(state.bankResolved).toBe(false);
    // The verification state comes from the profile alone and is published immediately.
    expect(state.hydrated).toBe(true);
    expect(state.verificationStatus).toBe(VerificationStatus.VERIFIED);
    expect(state.bankStatus).toBe(BankSetupStatus.NOT_CONNECTED);
  });

  it('does not touch the store while the profile is still loading', () => {
    profileMock.mockReturnValue({ profile: null });
    paymentMock.mockReturnValue(payment({ status: BankSetupStatus.COMPLETED }));

    render(<AssociationStatusSync />);

    expect(useAccStatusStore.getState().bankStatus).toBe(BankSetupStatus.NOT_CONNECTED);
    expect(useAccStatusStore.getState().done).toBe(0);
    // Consumers gate on `hydrated`, so nothing is displayed from these defaults.
    expect(useAccStatusStore.getState().hydrated).toBe(false);
  });

  it('stores the server-computed bank status and the provider action URL', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.PENDING));
    paymentMock.mockReturnValue(payment({
      status: BankSetupStatus.NEEDS_DATA,
      actionUrl: 'https://my.mollie.com/dashboard/onboarding',
    }));

    render(<AssociationStatusSync />);

    const state = useAccStatusStore.getState();
    expect(state.bankStatus).toBe(BankSetupStatus.NEEDS_DATA);
    expect(state.bankActionUrl).toBe('https://my.mollie.com/dashboard/onboarding');
    expect(state.done).toBe(0);
  });

  it('stores the payment provider of the association', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.VERIFIED));
    paymentMock.mockReturnValue(payment({ provider: PaymentProviderId.HELLOASSO }));

    render(<AssociationStatusSync />);

    expect(useAccStatusStore.getState().paymentProvider).toBe(PaymentProviderId.HELLOASSO);
  });

  it('counts a failed status read as resolved', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.VERIFIED));
    paymentMock.mockReturnValue(payment({ provider: null, status: null, error: 'common.errors.serverError' }));

    render(<AssociationStatusSync />);

    expect(useAccStatusStore.getState().bankResolved).toBe(true);
    expect(useAccStatusStore.getState().bankStatus).toBe(BankSetupStatus.NOT_CONNECTED);
  });

  it('keeps the payment status resolved while a manual refresh is in flight', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.VERIFIED));
    paymentMock.mockReturnValue(payment({ status: BankSetupStatus.COMPLETED }));

    const { rerender } = render(<AssociationStatusSync />);
    expect(useAccStatusStore.getState().bankResolved).toBe(true);

    paymentMock.mockReturnValue(payment({ status: BankSetupStatus.COMPLETED, isLoading: true }));
    rerender(<AssociationStatusSync />);

    expect(useAccStatusStore.getState().bankResolved).toBe(true);
  });

  it('propagates the rejection reason and marks both steps done when everything is complete', () => {
    profileMock.mockReturnValue(profile(VerificationStatus.REJECTED, 'Statuts illisibles'));
    paymentMock.mockReturnValue(payment({ status: BankSetupStatus.COMPLETED }));

    render(<AssociationStatusSync />);

    const state = useAccStatusStore.getState();
    expect(state.rejectionReason).toBe('Statuts illisibles');
    expect(state.bank).toBe(true);
    expect(state.verified).toBe(false);
    expect(state.done).toBe(1);
  });
});
