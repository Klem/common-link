import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AssociationProfilePage from '../page';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { PaymentProviderId } from '@/types/paymentProvider';
import { HelloAssoLink, HelloAssoWebhookState } from '@/types/helloasso';

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
vi.mock('@/components/dashboard/helloasso-onboarding/HelloAssoOnboardingFlow', () => ({
  default: () => <p>test.helloAssoFlow</p>,
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

function openBankTab(provider: PaymentProviderId | null, resolved = true) {
  useAccStatusStore.setState({ paymentProvider: provider, bankResolved: resolved });
  render(<AssociationProfilePage />);
  fireEvent.click(bankTabButton());
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('Bank tab — journey chosen by payment provider (S2-05)', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useAccStatusStore.setState({ helloasso: null });
  });

  it('renders the Mollie flow for a Mollie association', () => {
    openBankTab(PaymentProviderId.MOLLIE);

    expect(screen.getByText('test.mollieFlow')).toBeInTheDocument();
    expect(screen.queryByText('title')).not.toBeInTheDocument();
  });

  it('renders no journey while the payment status is not read yet (no Mollie flash for HelloAsso)', () => {
    openBankTab(null, false);

    expect(screen.queryByText('test.mollieFlow')).not.toBeInTheDocument();
    expect(screen.queryByText('test.helloAssoFlow')).not.toBeInTheDocument();
  });

  it('falls back to the Mollie flow when the status read failed (provider unknown)', () => {
    openBankTab(null);

    expect(screen.getByText('test.mollieFlow')).toBeInTheDocument();
  });

  it('renders the HelloAsso journey for a HelloAsso association', () => {
    openBankTab(PaymentProviderId.HELLOASSO);

    expect(screen.queryByText('test.mollieFlow')).not.toBeInTheDocument();
    expect(screen.getByText('test.helloAssoFlow')).toBeInTheDocument();
  });

  it('shows the HelloAsso badge, coloured by the link state', () => {
    useAccStatusStore.setState({
      helloasso: {
        link: HelloAssoLink.OK,
        notificationUrl: null,
        organizationSlug: 'les-amis',
        canCollect: false,
        everCompliant: true,
        lastEligibilityCheckAt: null,
        webhook: HelloAssoWebhookState.UNKNOWN,
        clientIdMasked: null,
        linkedAt: null,
        linkedBy: null,
        secretWipedAt: null,
        lastRefreshAt: null,
      },
    });
    openBankTab(PaymentProviderId.HELLOASSO);

    const badge = bankTabButton().querySelector('.set-tab-badge');
    expect(badge).toHaveTextContent('association.profile.tabs.helloAssoBadge.SUSPENDED');
    expect(badge).toHaveClass('err');
  });

  it('keeps the Mollie badge for a Mollie association', () => {
    openBankTab(PaymentProviderId.MOLLIE);

    expect(bankTabButton().querySelector('.set-tab-badge')?.textContent).toContain('association.profile.tabs.bankBadge.');
  });

  it('shows the renamed tab with the card icon once unlocked', () => {
    openBankTab(PaymentProviderId.MOLLIE);

    expect(bankTabButton().textContent).toContain('💳');
  });
});

describe('Diffusion tab and setup notice — HelloAsso association (A1)', () => {
  const helloAssoLinked = (canCollect: boolean) => ({
    link: HelloAssoLink.OK,
    notificationUrl: null,
    organizationSlug: 'les-amis',
    canCollect,
    everCompliant: canCollect,
    lastEligibilityCheckAt: null,
    webhook: HelloAssoWebhookState.UNKNOWN,
    clientIdMasked: null,
    linkedAt: null,
    linkedBy: null,
    secretWipedAt: null,
    lastRefreshAt: null,
  });
  const diffusionTabButton = (): HTMLElement =>
    screen.getByText((_, el) => el?.tagName === 'BUTTON' && !!el.textContent?.includes('association.profile.tabs.diffusion'));
  const NOTICE = 'association.profile.setupNotice.title';

  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('unlocks Diffusion and hides the setup notice when HelloAsso can collect', () => {
    useAccStatusStore.setState({
      paymentProvider: PaymentProviderId.HELLOASSO,
      bankResolved: true,
      helloasso: helloAssoLinked(true),
    });
    render(<AssociationProfilePage />);

    expect(diffusionTabButton()).toBeEnabled();
    expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
  });

  it('keeps Diffusion locked and shows the setup notice while HelloAsso cannot collect', () => {
    useAccStatusStore.setState({
      paymentProvider: PaymentProviderId.HELLOASSO,
      bankResolved: true,
      helloasso: helloAssoLinked(false),
    });
    render(<AssociationProfilePage />);

    expect(diffusionTabButton()).toBeDisabled();
    expect(screen.getByText(NOTICE)).toBeInTheDocument();
  });

  it('does not flash the setup notice before the payment status is read', () => {
    useAccStatusStore.setState({ paymentProvider: null, bankResolved: false, helloasso: null });
    render(<AssociationProfilePage />);

    expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
  });
});
