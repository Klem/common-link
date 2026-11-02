import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AssociationProfilePage from '../page';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { PaymentProviderId } from '@/types/paymentProvider';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => {
  const t = Object.assign((key: string) => key, { rich: (key: string) => key });
  return { useTranslations: () => t, useLocale: () => 'fr' };
});

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => ({ get: () => null, toString: () => '' }),
}));

vi.mock('@/hooks/dashboard/useAssociationProfile', () => ({
  useAssociationProfile: () => ({
    profile: { id: 'uuid-1', name: 'Asso', verificationStatus: 'VERIFIED', contactEmail: 'a@b.fr', contactName: 'A B' },
    isLoading: false,
    error: null,
    isSuccess: false,
    updateProfile: vi.fn(),
    refreshProfile: vi.fn(),
  }),
}));

// Signed mandate: the bank tab is unlocked.
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

vi.mock('@/hooks/mollie/useMollieKycStatus', () => ({
  useMollieKycStatus: () => ({
    connected: false,
    pending: false,
    broken: false,
    onboardingStatus: null,
    canReceivePayments: null,
    dashboardUrl: null,
    canForceComplete: false,
    complementRequested: false,
    lastUpdatedAt: null,
    isLoading: false,
    error: null,
    refresh: vi.fn(),
    forceRefresh: vi.fn(),
  }),
}));

vi.mock('@/components/dashboard/mollie-onboarding/MollieOnboardingFlow', () => ({
  default: () => <p>test.mollieFlow</p>,
}));
vi.mock('@/components/dashboard/MollieOnboardModal', () => ({ default: () => null }));
vi.mock('@/components/dashboard/MollieInfoModal', () => ({ default: () => null }));
vi.mock('@/components/settings/diffusion/DiffusionTab', () => ({ DiffusionTab: () => null }));
vi.mock('@/components/settings/VerificationTab', () => ({ VerificationTab: () => null }));
vi.mock('@/components/settings/MandateTab', () => ({ MandateTab: () => null }));
vi.mock('@/hooks/auth/useSetPassword', () => ({ useSetPassword: () => ({ onSubmit: vi.fn(), loading: false }) }));
vi.mock('@/stores/authStore', () => ({
  useAuthStore: (sel: (s: { user: { provider: 'EMAIL' } }) => unknown) => sel({ user: { provider: 'EMAIL' } }),
}));
vi.mock('@/components/dashboard/Topbar', () => ({ Topbar: () => null }));
vi.mock('@/components/auth/SetPasswordForm', () => ({ SetPasswordForm: () => null }));

// ── Helpers ──────────────────────────────────────────────────────────────────

const bankTabButton = (): HTMLElement =>
  screen.getByText((_, el) => el?.tagName === 'BUTTON' && !!el.textContent?.includes('association.profile.tabs.bank '));

function openBankTab(provider: PaymentProviderId | null) {
  useAccStatusStore.setState({ paymentProvider: provider });
  render(<AssociationProfilePage />);
  fireEvent.click(bankTabButton());
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('Bank tab — journey chosen by payment provider (S2-05)', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('renders the Mollie flow for a Mollie association', () => {
    openBankTab(PaymentProviderId.MOLLIE);

    expect(screen.getByText('test.mollieFlow')).toBeInTheDocument();
    expect(screen.queryByText('title')).not.toBeInTheDocument();
  });

  it('keeps the Mollie flow while the provider is still unknown', () => {
    openBankTab(null);

    expect(screen.getByText('test.mollieFlow')).toBeInTheDocument();
  });

  it('renders the coming-soon placeholder for a HelloAsso association', () => {
    openBankTab(PaymentProviderId.HELLOASSO);

    expect(screen.queryByText('test.mollieFlow')).not.toBeInTheDocument();
    // next-intl is mocked to echo keys; the placeholder's namespace is resolved by its own useTranslations.
    expect(screen.getByRole('status')).toHaveTextContent('title');
    expect(screen.getByRole('status')).toHaveTextContent('body');
  });

  it('shows the renamed tab with the card icon once unlocked', () => {
    openBankTab(PaymentProviderId.MOLLIE);

    expect(bankTabButton().textContent).toContain('💳');
  });
});
