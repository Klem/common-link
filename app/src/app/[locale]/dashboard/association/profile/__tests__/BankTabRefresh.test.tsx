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

vi.mock('@/components/settings/diffusion/DiffusionTab', () => ({ DiffusionTab: () => <p>test.diffusionTab</p> }));
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
    window.localStorage.clear();
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

  it('keeps the retry notice after retry then cancel, even while the server still reports pending', async () => {
    mollie = { pending: true };
    openBankTab();
    fireEvent.click(screen.getByText('test.popupClosed'));
    fireEvent.click(await screen.findByText('notices.retry'));

    // The modal is cancelled (it only calls onClose): the interrupted notice must still win over
    // the pending one, whose action is disabled, so the user can retry again.
    expect(screen.getByText('notices.interrupted')).toBeInTheDocument();
    expect(screen.queryByText('notices.pending')).not.toBeInTheDocument();
    expect(screen.getByText('notices.retry')).toBeInTheDocument();
  });

  it('remembers the active tab and reopens on it after a reload', async () => {
    const first = openBankTab();
    first.unmount();

    render(<AssociationProfilePage />);
    await waitFor(() => expect(tabButton('bank ')).toHaveClass('active'));
  });

  it('lets an explicit ?tab= deep link win over the remembered tab', async () => {
    window.localStorage.setItem('cl.associationProfile.activeTab.uuid-1', 'bank');
    tabParam = 'verif';
    render(<AssociationProfilePage />);

    await waitFor(() => expect(tabButton('verif')).toHaveClass('active'));
  });

  it('falls back to the first tab when the remembered one is still locked', async () => {
    window.localStorage.setItem('cl.associationProfile.activeTab.uuid-1', 'diffusion');
    render(<AssociationProfilePage />);

    await waitFor(() => expect(tabButton('infos')).toHaveClass('active'));
  });

  it('stays on the bank tab after a successful connection (Q5)', async () => {
    openBankTab();
    fireEvent.click(screen.getByText('test.connected'));

    await waitFor(() => expect(forceRefresh).toHaveBeenCalledTimes(1));
    expect(tabButton('bank ')).toHaveClass('active');
    expect(screen.queryByText('test.diffusionTab')).not.toBeInTheDocument();
  });

  it('keeps the Diffusion tab locked after the attestation of a COMPLETED account without payments', async () => {
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: false };
    openBankTab();

    const box = await screen.findByRole('checkbox', { name: 'attest.label' });
    await waitFor(() => expect(box).not.toBeDisabled());
    fireEvent.click(box);
    await waitFor(() => expect(box).toBeChecked());

    expect(tabButton('diffusion')).toBeDisabled();
  });

  it('applies ?tab= once: an account becoming active while on the bank tab stays there (R9)', () => {
    tabParam = 'diffusion';
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.IN_REVIEW, canReceivePayments: false };
    const { rerender } = render(<AssociationProfilePage />);
    // Diffusion is locked on arrival: the deep link falls back to "infos".
    expect(tabButton('infos')).toHaveClass('active');

    fireEvent.click(tabButton('bank '));
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true };
    rerender(<AssociationProfilePage />);

    expect(tabButton('bank ')).toHaveClass('active');
    expect(tabButton('diffusion')).not.toBeDisabled();
    expect(screen.queryByText('test.diffusionTab')).not.toBeInTheDocument();
  });

  it('applies an unlocked ?tab= on the first complete load, as before', () => {
    tabParam = 'diffusion';
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true };
    render(<AssociationProfilePage />);

    expect(screen.getByText('test.diffusionTab')).toBeInTheDocument();
  });

  it('shows no Diffusion shortcut on screen 6: the settings tabs already lead there', () => {
    mollie = { connected: true, onboardingStatus: MollieOnboardingStatus.COMPLETED, canReceivePayments: true };
    openBankTab();

    expect(screen.queryByRole('button', { name: /^tabs\.diffusion/ })).not.toBeInTheDocument();
  });
});
