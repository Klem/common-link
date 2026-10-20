import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import AssociationProfilePage from '../page';
import type { UseMollieKycStatusReturn } from '@/hooks/mollie/useMollieKycStatus';
import { MollieOnboardingStatus } from '@/types/mollie-connect';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => {
  const t = Object.assign((key: string) => key, { rich: (key: string) => key });
  return { useTranslations: () => t, useLocale: () => 'fr' };
});

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => ({ get: () => null }),
}));

vi.mock('@/hooks/dashboard/useAssociationProfile', () => ({
  useAssociationProfile: () => ({
    profile: { id: 'uuid-1', name: 'Asso', verificationStatus: 'VERIFIED' },
    isLoading: false,
    error: null,
    isSuccess: false,
    updateProfile: vi.fn(),
    refreshProfile: vi.fn(),
  }),
}));

vi.mock('@/hooks/dashboard/useMandate', () => ({
  useMandate: () => ({
    state: { signed: true, mandateDocs: [], blocked: false },
    isLoading: false,
    uploadDoc: vi.fn(),
    deleteDoc: vi.fn(),
    sign: vi.fn(),
    revoke: vi.fn(),
    downloadPdf: vi.fn(),
  }),
}));

let mollie: Partial<UseMollieKycStatusReturn> = {};
vi.mock('@/hooks/mollie/useMollieKycStatus', () => ({
  useMollieKycStatus: () => ({
    connected: false,
    pending: false,
    broken: false,
    onboardingStatus: null,
    canReceivePayments: null,
    dashboardUrl: null,
    canForceComplete: false,
    isLoading: false,
    error: null,
    refresh: vi.fn(),
    ...mollie,
  }),
}));

vi.mock('@/components/settings/diffusion/DiffusionTab', () => ({ DiffusionTab: () => null }));
vi.mock('@/components/settings/VerificationTab', () => ({ VerificationTab: () => null }));
vi.mock('@/components/settings/MandateTab', () => ({ MandateTab: () => null }));
vi.mock('@/hooks/auth/useSetPassword', () => ({ useSetPassword: () => ({ onSubmit: vi.fn(), loading: false }) }));
vi.mock('@/stores/authStore', () => ({
  useAuthStore: (sel: (s: { user: { provider: 'EMAIL' } }) => unknown) => sel({ user: { provider: 'EMAIL' } }),
}));
vi.mock('@/components/dashboard/Topbar', () => ({ Topbar: () => null }));
vi.mock('@/components/auth/SetPasswordForm', () => ({ SetPasswordForm: () => null }));

// ── Tests ────────────────────────────────────────────────────────────────────

/** The badge inside the "bank" tab button (the button label is the `tabs.bank` key). */
function bankBadge(): HTMLElement {
  const button = screen.getByText((_, el) => el?.tagName === 'BUTTON' && !!el.textContent?.includes('association.profile.tabs.bank '));
  const badge = button.querySelector('.set-tab-badge');
  if (!badge) throw new Error('bank badge not found');
  return badge as HTMLElement;
}

describe('Bank tab badge — profile/page.tsx (Q7)', () => {
  beforeEach(() => {
    mollie = {};
  });

  it.each([
    [{}, 'notConnected'],
    [{ connected: true, onboardingStatus: MollieOnboardingStatus.NEEDS_DATA }, 'toComplete'],
    [{ connected: true, onboardingStatus: MollieOnboardingStatus.IN_REVIEW }, 'inReview'],
    [{ connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: false }, 'paymentsNotEnabled'],
    [{ connected: true, broken: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true }, 'reconnect'],
  ])('labels %o as %s without the ok class', (state, label) => {
    mollie = state;
    render(<AssociationProfilePage />);

    expect(bankBadge()).toHaveTextContent(`association.profile.tabs.bankBadge.${label}`);
    expect(bankBadge()).not.toHaveClass('ok');
  });

  it('carries the ok class exactly when bankDone, which also unlocks the diffusion tab', () => {
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true };
    render(<AssociationProfilePage />);

    expect(bankBadge()).toHaveTextContent('association.profile.tabs.bankBadge.active');
    expect(bankBadge()).toHaveClass('ok');
    expect(screen.getByText((_, el) => el?.tagName === 'BUTTON' && !!el.textContent?.includes('association.profile.tabs.diffusion'))).not.toBeDisabled();
  });
});
