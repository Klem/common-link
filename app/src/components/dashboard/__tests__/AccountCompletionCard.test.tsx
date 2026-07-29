import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AccountCompletionCard } from '../AccountCompletionCard';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';
import { useAuthStore } from '@/stores/authStore';
import { UserRole, AuthProvider } from '@/types/auth';

const push = vi.fn();

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, unknown>) => {
    if (params) return key + ':' + JSON.stringify(params);
    return key;
  },
  useLocale: () => 'fr',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push }),
}));

const USER_A = 'aaaaaaaa-0000-0000-0000-000000000001';
const USER_B = 'bbbbbbbb-0000-0000-0000-000000000002';

/** Logs a user in so the card can scope its dismissal key. */
function signIn(id: string) {
  useAuthStore.setState({
    user: {
      id,
      email: `${id}@test.org`,
      role: UserRole.ASSOCIATION,
      displayName: 'Asso',
      provider: AuthProvider.EMAIL,
      emailVerified: true,
      createdAt: '2026-01-01T00:00:00Z',
    },
    accessToken: 'token',
    isAuthenticated: true,
    isLoading: false,
  });
}

let mockLocalStorage: Record<string, string> = {};
beforeEach(() => {
  Object.defineProperty(window, 'localStorage', {
    value: {
      getItem: (k: string) => mockLocalStorage[k] ?? null,
      setItem: (k: string, v: string) => { mockLocalStorage[k] = v; },
    },
    writable: true,
  });
  mockLocalStorage = {};
  push.mockClear();
  signIn(USER_A);
});

function renderCard(overrides: Partial<React.ComponentProps<typeof AccountCompletionCard>> = {}) {
  return render(
    <AccountCompletionCard
      verificationStatus={VerificationStatus.UNVERIFIED}
      bankStatus={BankSetupStatus.NOT_CONNECTED}
      rejectionReason={null}
      mollieDashboardUrl={null}
      {...overrides}
    />,
  );
}

/** CSS variant of a rendered row, by DOM order: 0 = KYC, 1 = bank. */
function variantOf(index: 0 | 1): string {
  const rows = document.querySelectorAll('.acc-check');
  return rows[index].className.replace('acc-check ', '');
}

describe('AccountCompletionCard — layout', () => {
  it('always renders both check rows, KYC first, whatever the statuses', () => {
    renderCard({ verificationStatus: VerificationStatus.VERIFIED, bankStatus: BankSetupStatus.COMPLETED });
    const rows = document.querySelectorAll('.acc-check');
    expect(rows).toHaveLength(2);
    // Row order is what `variantOf` relies on in every per-status test below.
    expect(rows[0].textContent).toContain('checks.kyc.title');
    expect(rows[1].textContent).toContain('checks.bank.title');
    expect(screen.getAllByText('checks.validated')).toHaveLength(2);
  });

  it('stays visible when everything is complete (recap mode)', () => {
    const { container } = renderCard({
      verificationStatus: VerificationStatus.VERIFIED,
      bankStatus: BankSetupStatus.COMPLETED,
    });
    expect(container.firstChild).not.toBeNull();
    expect(screen.getByText('sub.allDone')).toBeInTheDocument();
  });

  it('hides on dismiss and persists the choice under a user-scoped key', () => {
    renderCard();
    fireEvent.click(screen.getByTitle('dismiss'));
    expect(mockLocalStorage[`cl-acc-card-dismissed:${USER_A}`]).toBe('1');
    expect(screen.queryByText('title')).not.toBeInTheDocument();
  });

  it('stays hidden for the same user across sessions', () => {
    mockLocalStorage[`cl-acc-card-dismissed:${USER_A}`] = '1';
    renderCard();
    expect(screen.queryByText('title')).not.toBeInTheDocument();
  });

  /**
   * `localStorage` outlives the session entirely: without the user-scoped key, dismissing the
   * card as one association would hide it for every other account using the same browser.
   */
  it('reappears for another user who logs in from the same browser', () => {
    const { unmount } = renderCard();
    fireEvent.click(screen.getByTitle('dismiss'));
    unmount();

    signIn(USER_B);
    renderCard();
    expect(screen.getByText('title')).toBeInTheDocument();
    expect(mockLocalStorage[`cl-acc-card-dismissed:${USER_B}`]).toBeUndefined();
  });
});

describe('AccountCompletionCard — KYC row per VerificationStatus', () => {
  it('UNVERIFIED: todo row with a verify CTA', () => {
    renderCard({ verificationStatus: VerificationStatus.UNVERIFIED });
    expect(variantOf(0)).toBe('todo');
    expect(screen.getByText('checks.kyc.UNVERIFIED.desc')).toBeInTheDocument();
    expect(screen.getByText('checks.kyc.UNVERIFIED.cta')).toBeInTheDocument();
  });

  it('PENDING: pending row with an ETA and no CTA', () => {
    renderCard({ verificationStatus: VerificationStatus.PENDING });
    expect(variantOf(0)).toBe('pending');
    expect(screen.getByText('checks.kyc.PENDING.eta')).toBeInTheDocument();
    expect(screen.queryByText('checks.kyc.PENDING.cta')).not.toBeInTheDocument();
  });

  it('VERIFIED: done row showing the validated label', () => {
    renderCard({ verificationStatus: VerificationStatus.VERIFIED });
    expect(variantOf(0)).toBe('done');
    expect(screen.getByText('checks.validated')).toBeInTheDocument();
  });

  it('REJECTED: rejected row with the back-office reason and a fix CTA', () => {
    renderCard({
      verificationStatus: VerificationStatus.REJECTED,
      rejectionReason: 'Statuts illisibles',
    });
    expect(variantOf(0)).toBe('rejected');
    expect(screen.getByText('Statuts illisibles')).toBeInTheDocument();
    expect(screen.getByText('checks.kyc.REJECTED.cta')).toBeInTheDocument();
    expect(screen.getByText('sub.rejected')).toBeInTheDocument();
  });

  it('REJECTED without a reason: no reason block, CTA still present', () => {
    renderCard({ verificationStatus: VerificationStatus.REJECTED, rejectionReason: null });
    expect(document.querySelector('.acc-check-reason')).toBeNull();
    expect(screen.getByText('checks.kyc.REJECTED.cta')).toBeInTheDocument();
  });

  it('routes the KYC CTA to the verification tab', () => {
    renderCard({ verificationStatus: VerificationStatus.UNVERIFIED });
    fireEvent.click(screen.getByText('checks.kyc.UNVERIFIED.cta'));
    expect(push).toHaveBeenCalledWith(expect.stringContaining('tab=verif'));
  });
});

describe('AccountCompletionCard — bank row per BankSetupStatus', () => {
  it('NOT_CONNECTED: todo row with a connect CTA routed to the bank tab', () => {
    renderCard({ bankStatus: BankSetupStatus.NOT_CONNECTED });
    expect(variantOf(1)).toBe('todo');
    fireEvent.click(screen.getByText('checks.bank.NOT_CONNECTED.cta'));
    expect(push).toHaveBeenCalledWith(expect.stringContaining('tab=bank'));
  });

  it('NEEDS_DATA: pending row opening the Mollie wizard in a new tab', () => {
    const open = vi.fn();
    vi.stubGlobal('open', open);
    renderCard({
      bankStatus: BankSetupStatus.NEEDS_DATA,
      mollieDashboardUrl: 'https://my.mollie.com/dashboard/onboarding',
    });
    expect(variantOf(1)).toBe('pending');
    fireEvent.click(screen.getByText('checks.bank.NEEDS_DATA.cta'));
    expect(open).toHaveBeenCalledWith(
      'https://my.mollie.com/dashboard/onboarding',
      '_blank',
      'noopener,noreferrer',
    );
    expect(push).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('NEEDS_DATA without a dashboard URL: falls back to the bank tab', () => {
    renderCard({ bankStatus: BankSetupStatus.NEEDS_DATA, mollieDashboardUrl: null });
    fireEvent.click(screen.getByText('checks.bank.NEEDS_DATA.cta'));
    expect(push).toHaveBeenCalledWith(expect.stringContaining('tab=bank'));
  });

  it('IN_REVIEW: pending row with an ETA and no CTA', () => {
    renderCard({ bankStatus: BankSetupStatus.IN_REVIEW });
    expect(variantOf(1)).toBe('pending');
    expect(screen.getByText('checks.bank.IN_REVIEW.eta')).toBeInTheDocument();
    expect(screen.queryByText('checks.bank.IN_REVIEW.cta')).not.toBeInTheDocument();
  });

  it('COMPLETED: done row showing the validated label', () => {
    renderCard({ bankStatus: BankSetupStatus.COMPLETED });
    expect(variantOf(1)).toBe('done');
    expect(screen.getByText('checks.validated')).toBeInTheDocument();
  });

  it('BROKEN: rejected row with a reconnect CTA', () => {
    renderCard({ bankStatus: BankSetupStatus.BROKEN });
    expect(variantOf(1)).toBe('rejected');
    expect(screen.getByText('checks.bank.BROKEN.cta')).toBeInTheDocument();
  });
});

describe('AccountCompletionCard — regression: verified association without Mollie', () => {
  /**
   * The reported bug: a VERIFIED association that never connected Mollie was shown a
   * "pending verification" row. The KYC row must be green whatever the bank status.
   */
  it('shows the KYC row as done when VERIFIED and Mollie was never connected', () => {
    renderCard({
      verificationStatus: VerificationStatus.VERIFIED,
      bankStatus: BankSetupStatus.NOT_CONNECTED,
    });
    expect(variantOf(0)).toBe('done');
    expect(variantOf(1)).toBe('todo');
    expect(screen.getByText('sub.bankOnly')).toBeInTheDocument();
  });
});
