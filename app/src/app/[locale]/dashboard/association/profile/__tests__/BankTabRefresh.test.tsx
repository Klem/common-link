import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import AssociationProfilePage from '../page';
import type { UseMollieKycStatusReturn } from '@/hooks/mollie/useMollieKycStatus';
import { MollieOnboardingStatus } from '@/types/mollie-connect';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => {
  const t = Object.assign((key: string) => key, { rich: (key: string) => key });
  return { useTranslations: () => t, useLocale: () => 'fr' };
});

let tabParam: string | null = null;
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => ({ get: () => tabParam, toString: () => (tabParam ? `tab=${tabParam}` : '') }),
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

const { forceRefresh, refresh, mockAttest } = vi.hoisted(() => ({
  forceRefresh: vi.fn(),
  refresh: vi.fn(),
  mockAttest: vi.fn(),
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
    complementRequested: false,
    lastUpdatedAt: null,
    isLoading: false,
    error: null,
    refresh,
    forceRefresh,
    ...mollie,
  }),
}));

vi.mock('@/lib/api/mollie-connect', () => ({
  getMollieOnboardingContext: vi.fn().mockResolvedValue({
    structureVariant: 'DECLARED',
    verifiedAt: null,
    beneficialOwners: [],
    providedDocuments: [],
    siren: null,
    selfCompletionAttestedAt: null,
  }),
  attestMollieSelfCompletion: mockAttest,
  forceCompleteMollieOnboarding: vi.fn(),
}));

// The OAuth modal is replaced by two buttons firing its callbacks.
vi.mock('@/components/dashboard/MollieOnboardModal', () => ({
  default: ({ onConnected, onPopupClosed }: { onConnected: () => void; onPopupClosed?: () => void }) => (
    <>
      <button type="button" onClick={() => onPopupClosed?.()}>test.popupClosed</button>
      <button type="button" onClick={() => onConnected()}>test.connected</button>
    </>
  ),
}));

vi.mock('@/components/settings/WidgetTab', () => ({ WidgetTab: () => <p>test.widgetTab</p> }));
vi.mock('@/components/settings/LandingTab', () => ({ LandingTab: () => null }));
vi.mock('@/components/settings/VerificationTab', () => ({ VerificationTab: () => null }));
vi.mock('@/components/settings/MandateTab', () => ({ MandateTab: () => null }));
vi.mock('@/hooks/auth/useSetPassword', () => ({ useSetPassword: () => ({ onSubmit: vi.fn(), loading: false }) }));
vi.mock('@/stores/authStore', () => ({
  useAuthStore: (sel: (s: { user: { provider: 'EMAIL' } }) => unknown) => sel({ user: { provider: 'EMAIL' } }),
}));
vi.mock('@/components/dashboard/Topbar', () => ({ Topbar: () => null }));
vi.mock('@/components/auth/SetPasswordForm', () => ({ SetPasswordForm: () => null }));

// ── Helpers ──────────────────────────────────────────────────────────────────

const tabButton = (key: string): HTMLElement =>
  screen.getByText((_, el) => el?.tagName === 'BUTTON' && !!el.textContent?.includes(`association.profile.tabs.${key}`));

function openBankTab() {
  const view = render(<AssociationProfilePage />);
  fireEvent.click(tabButton('bank '));
  return view;
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('Bank tab — status refresh and navigation (S4)', () => {
  beforeEach(() => {
    mollie = {};
    tabParam = null;
    forceRefresh.mockReset().mockResolvedValue(undefined);
    refresh.mockReset().mockResolvedValue(undefined);
    mockAttest.mockReset().mockResolvedValue({ attestedAt: '2026-10-12T09:41:00Z' });
  });

  it('forces a status refresh when the OAuth popup closes', async () => {
    openBankTab();
    fireEvent.click(screen.getByText('test.popupClosed'));

    await waitFor(() => expect(forceRefresh).toHaveBeenCalledTimes(1));
    // Automatic trigger: no error toast (R8).
    expect(forceRefresh).toHaveBeenCalledWith({ silent: true });
    // The flow now shows the interrupted-connection notice.
    expect(await screen.findByText('notices.interrupted')).toBeInTheDocument();
  });

  it('stays on the bank tab after a successful connection (Q5)', async () => {
    openBankTab();
    fireEvent.click(screen.getByText('test.connected'));

    await waitFor(() => expect(forceRefresh).toHaveBeenCalledTimes(1));
    expect(tabButton('bank ')).toHaveClass('active');
    expect(screen.queryByText('test.widgetTab')).not.toBeInTheDocument();
  });

  it('keeps Widget and Landing locked after the attestation of a COMPLETED account without payments', async () => {
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: false };
    openBankTab();

    const box = await screen.findByRole('checkbox', { name: 'attest.label' });
    await waitFor(() => expect(box).not.toBeDisabled());
    fireEvent.click(box);
    await waitFor(() => expect(box).toBeChecked());

    expect(tabButton('widget')).toBeDisabled();
    expect(tabButton('landing')).toBeDisabled();
  });

  it('applies ?tab= once: an account becoming active while on the bank tab stays there (R9)', () => {
    tabParam = 'widget';
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.IN_REVIEW, canReceivePayments: false };
    const { rerender } = render(<AssociationProfilePage />);
    // Widget is locked on arrival: the deep link falls back to "infos".
    expect(tabButton('infos')).toHaveClass('active');

    fireEvent.click(tabButton('bank '));
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true };
    rerender(<AssociationProfilePage />);

    expect(tabButton('bank ')).toHaveClass('active');
    expect(tabButton('widget')).not.toBeDisabled();
    expect(screen.queryByText('test.widgetTab')).not.toBeInTheDocument();
  });

  it('applies an unlocked ?tab= on the first complete load, as before', () => {
    tabParam = 'widget';
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true };
    render(<AssociationProfilePage />);

    expect(screen.getByText('test.widgetTab')).toBeInTheDocument();
  });

  it('opens the Widget tab from screen 6 once collecting', async () => {
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true };
    openBankTab();

    fireEvent.click(screen.getByRole('button', { name: /^tabs\.widget/ }));
    expect(await screen.findByText('test.widgetTab')).toBeInTheDocument();
  });
});
