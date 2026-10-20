import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
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

const NOTICE = 'association.profile.setupNotice.title';
const KEY = 'cl.associationProfile.setupNoticeDismissed';

describe('Setup notice — profile/page.tsx', () => {
  beforeEach(() => {
    mollie = {};
    sessionStorage.clear();
  });

  it('is shown while the bank setup is not COMPLETED', async () => {
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.IN_REVIEW };
    render(<AssociationProfilePage />);

    expect(await screen.findByText(NOTICE)).toBeInTheDocument();
  });

  it('is hidden once the bank setup is COMPLETED', () => {
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true };
    render(<AssociationProfilePage />);

    expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
  });

  it('is hidden while the Mollie status loads', () => {
    mollie = { isLoading: true };
    render(<AssociationProfilePage />);

    expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
  });

  it('closes for the rest of the session', async () => {
    const { unmount } = render(<AssociationProfilePage />);
    fireEvent.click(await screen.findByRole('button', { name: 'association.profile.setupNotice.close' }));

    expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
    expect(sessionStorage.getItem(KEY)).toBe('1');

    unmount();
    render(<AssociationProfilePage />);
    expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
  });
});
