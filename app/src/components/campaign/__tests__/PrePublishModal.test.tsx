import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { PrePublishModal } from '../PrePublishModal';
import type { CampaignDto } from '@/types/campaign';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';
import { getLegalAcceptanceState } from '@/lib/api/legal';
import { getLegalDocument } from '@/lib/api/public';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

vi.mock('@/lib/api/legal', () => ({
  getLegalAcceptanceState: vi.fn(),
}));

vi.mock('@/lib/api/public', () => ({
  getLegalDocument: vi.fn(),
}));

/** Balanced budget: one expense section and one revenue section for the same total. */
function balancedBudget(expenses = 10_000, revenues = 10_000) {
  return [
    { side: 'EXPENSE', code: 'CHARGES', name: 'Charges', items: [{ label: 'Achat', amount: expenses }] },
    { side: 'REVENUE', code: 'PRODUITS', name: 'Produits', items: [{ label: 'Dons', amount: revenues }] },
  ];
}

/** Campaign satisfying every blocking requirement, so tests isolate the block under test. */
function campaign(overrides: Record<string, unknown> = {}): CampaignDto {
  return {
    name: 'Campagne test',
    description: 'Une description suffisamment longue',
    startDate: '2026-01-01',
    endDate: '2026-06-01',
    goal: 10_000,
    budgetSections: balancedBudget(),
    milestones: [],
    reason: null,
    impactGoals: ' 200 repas servis chaque semaine pendant six mois',
    ...overrides,
  } as unknown as CampaignDto;
}

/** Renders the modal with a publishable campaign and a fully onboarded account, minus overrides. */
function renderWith(overrides: Partial<React.ComponentProps<typeof PrePublishModal>> = {}) {
  const props = {
    campaign: campaign(),
    verificationStatus: VerificationStatus.VERIFIED,
    bankStatus: BankSetupStatus.COMPLETED,
    mollieResolved: true,
    mollieDashboardUrl: null,
    onClose: vi.fn(),
    onConfirm: vi.fn(),
    ...overrides,
  };
  return render(<PrePublishModal {...props} />);
}

beforeEach(() => {
  // Default: CGU already accepted, so pre-existing tests exercising other gates aren't also
  // blocked on a checkbox they don't know about.
  vi.mocked(getLegalAcceptanceState).mockResolvedValue({
    documentType: 'CGU',
    currentVersion: '2026-08-26',
    accepted: true,
  });
});

describe('PrePublishModal — account status', () => {
  /** Regression guard: a loading Mollie status used to render as "no bank account connected". */
  it('shows a neutral loading row while the Mollie status is unresolved', () => {
    renderWith({ mollieResolved: false, bankStatus: BankSetupStatus.NOT_CONNECTED });

    expect(screen.getByText('account.loading')).toBeInTheDocument();
    expect(screen.queryByText('account.bank.NOT_CONNECTED')).not.toBeInTheDocument();
  });

  it('still shows the verification status while the Mollie status is unresolved', () => {
    renderWith({ mollieResolved: false, verificationStatus: VerificationStatus.PENDING });

    expect(screen.getByText('account.verif.PENDING')).toBeInTheDocument();
  });

  it('collapses to a single "complete" row when verified and Mollie is COMPLETED', () => {
    renderWith();

    expect(screen.getByText('account.complete')).toBeInTheDocument();
  });

  /** Regression guard: IN_REVIEW used to render as "no bank account connected". */
  it.each([
    BankSetupStatus.NOT_CONNECTED,
    BankSetupStatus.NEEDS_DATA,
    BankSetupStatus.IN_REVIEW,
    BankSetupStatus.BROKEN,
  ])('renders the Mollie status %s with its own message', (bankStatus) => {
    renderWith({ bankStatus });

    expect(screen.getByText(`account.bank.${bankStatus}`)).toBeInTheDocument();
  });

  /** Regression guard: PENDING and REJECTED used to render as "not yet verified". */
  it.each([
    VerificationStatus.UNVERIFIED,
    VerificationStatus.PENDING,
    VerificationStatus.REJECTED,
  ])('renders the verification status %s with its own message', (verificationStatus) => {
    renderWith({ verificationStatus });

    expect(screen.getByText(`account.verif.${verificationStatus}`)).toBeInTheDocument();
  });

  it('offers the Mollie wizard link only when onboarding data is missing', () => {
    renderWith({
      bankStatus: BankSetupStatus.NEEDS_DATA,
      mollieDashboardUrl: 'https://my.mollie.com/dashboard/onboarding',
    });

    expect(screen.getByText('account.completeBank')).toHaveAttribute(
      'href',
      'https://my.mollie.com/dashboard/onboarding',
    );
  });

  it('disables publishing until the Mollie status is known', () => {
    renderWith({ mollieResolved: false });

    expect(screen.getByText('complete').closest('button')).toBeDisabled();
  });

  it('enables publishing when everything is filled and Mollie is COMPLETED', async () => {
    renderWith();

    await waitFor(() => expect(screen.getByText('confirm').closest('button')).toBeEnabled());
  });

  it('blocks publishing when Mollie is not COMPLETED', () => {
    renderWith({ bankStatus: BankSetupStatus.IN_REVIEW });

    expect(screen.getByText('complete').closest('button')).toBeDisabled();
  });

  /**
   * LCB-FT: a campaign must not go live before the KYB dossier is validated. The KYC rows used to
   * be informational only, so an unverified association could publish. Mirrored server-side in
   * `CampaignService.preparePublish` (rule 8).
   */
  it.each([
    VerificationStatus.UNVERIFIED,
    VerificationStatus.PENDING,
    VerificationStatus.REJECTED,
  ])('blocks publishing when the KYB dossier is %s', (verificationStatus) => {
    renderWith({ verificationStatus });

    expect(screen.getByText('complete').closest('button')).toBeDisabled();
  });
});

/**
 * A donor is asked for money against a costed plan and a declared result, so the budget
 * prévisionnel and the expected outcome block publication instead of merely being recommended.
 * Both predicates are mirrored in `CampaignService.preparePublish` (rule 8).
 */
describe('PrePublishModal — budget and expected outcome are blocking', () => {
  it('lists them under the required section, not the recommended one', () => {
    renderWith();

    expect(screen.getByText('required.budget')).toBeInTheDocument();
    expect(screen.getByText('required.impactGoals')).toBeInTheDocument();
    expect(screen.queryByText('recommended.budget')).not.toBeInTheDocument();
    expect(screen.queryByText('recommended.impactGoals')).not.toBeInTheDocument();
  });

  it('blocks publishing when no budget has been entered', () => {
    renderWith({ campaign: campaign({ budgetSections: [] }) });

    expect(screen.getByText('complete').closest('button')).toBeDisabled();
  });

  it('blocks publishing when expenses and revenues do not match', () => {
    renderWith({ campaign: campaign({ budgetSections: balancedBudget(10_000, 8_000) }) });

    expect(screen.getByText('complete').closest('button')).toBeDisabled();
  });

  /** Tolerance mirrors the backend: a sub-euro rounding gap must not block a publish. */
  it('accepts a budget off by less than one euro', async () => {
    renderWith({ campaign: campaign({ budgetSections: balancedBudget(10_000, 10_000.4) }) });

    await waitFor(() => expect(screen.getByText('confirm').closest('button')).toBeEnabled());
  });

  it.each([null, '', 'Trop court'])(
    'blocks publishing when the expected outcome is %p',
    (impactGoals) => {
      renderWith({ campaign: campaign({ impactGoals }) });

      expect(screen.getByText('complete').closest('button')).toBeDisabled();
    },
  );
});

/**
 * Art. 1740 A CGI proof of acceptance. Mirrored server-side in
 * `CampaignService.preparePublish` / `LegalAcceptanceService`.
 */
describe('PrePublishModal — CGU acceptance is blocking', () => {
  it('blocks publishing until the CGU checkbox is checked, when not yet accepted', async () => {
    vi.mocked(getLegalAcceptanceState).mockResolvedValue({
      documentType: 'CGU',
      currentVersion: '2026-08-26',
      accepted: false,
    });
    renderWith();

    await waitFor(() => expect(screen.getByRole('checkbox', { name: /cgu\.label/ })).toBeInTheDocument());
    expect(screen.getByText('complete').closest('button')).toBeDisabled();

    fireEvent.click(screen.getByRole('checkbox', { name: /cgu\.label/ }));

    expect(screen.getByText('confirm').closest('button')).toBeEnabled();
  });

  it('renders the checkbox pre-checked and disabled when already accepted, and enables publishing', async () => {
    renderWith();

    await waitFor(() => {
      const checkbox = screen.getByRole('checkbox', { name: /cgu\.label/ }) as HTMLInputElement;
      expect(checkbox.checked).toBe(true);
      expect(checkbox).toBeDisabled();
    });
    expect(screen.getByText('confirm').closest('button')).toBeEnabled();
  });

  it('passes cguAccepted=true to onConfirm when publishing', async () => {
    const onConfirm = vi.fn();
    renderWith({ onConfirm });

    await waitFor(() => expect(screen.getByText('confirm').closest('button')).toBeEnabled());
    fireEvent.click(screen.getByText('confirm'));

    expect(onConfirm).toHaveBeenCalledWith(true);
  });

  it('opens the CGU text in a modal instead of navigating away', async () => {
    vi.mocked(getLegalDocument).mockResolvedValue({
      documentType: 'CGU',
      version: '2026-08-26',
      content: 'Texte des CGU.',
      publishedAt: '2026-08-26T00:00:00Z',
    });
    renderWith();

    await waitFor(() => expect(screen.getByRole('checkbox', { name: /cgu\.label/ })).toBeInTheDocument());
    fireEvent.click(screen.getByText('cgu.link'));

    expect(getLegalDocument).toHaveBeenCalledWith('CGU');
    await waitFor(() => {
      expect(screen.getByText('Texte des CGU.')).toBeInTheDocument();
    });
  });

  it('opening the CGU text does not itself check the unaccepted checkbox', async () => {
    vi.mocked(getLegalAcceptanceState).mockResolvedValue({
      documentType: 'CGU',
      currentVersion: '2026-08-26',
      accepted: false,
    });
    vi.mocked(getLegalDocument).mockResolvedValue({
      documentType: 'CGU',
      version: '2026-08-26',
      content: 'Texte des CGU.',
      publishedAt: '2026-08-26T00:00:00Z',
    });
    renderWith();

    await waitFor(() => expect(screen.getByRole('checkbox', { name: /cgu\.label/ })).toBeInTheDocument());
    fireEvent.click(screen.getByText('cgu.link'));
    await waitFor(() => screen.getByText('Texte des CGU.'));

    const checkbox = screen.getByRole('checkbox', { name: /cgu\.label/ }) as HTMLInputElement;
    expect(checkbox.checked).toBe(false);
    expect(screen.getByText('complete').closest('button')).toBeDisabled();
  });
});
