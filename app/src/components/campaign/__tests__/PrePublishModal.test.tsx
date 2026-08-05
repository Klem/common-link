import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PrePublishModal } from '../PrePublishModal';
import type { CampaignDto } from '@/types/campaign';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

/** Campaign satisfying every blocking requirement, so tests isolate the account-status block. */
function campaign(): CampaignDto {
  return {
    name: 'Campagne test',
    description: 'Une description suffisamment longue',
    startDate: '2026-01-01',
    endDate: '2026-06-01',
    goal: 10_000,
    budgetSections: [],
    milestones: [],
    reason: null,
    impactGoals: null,
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

  it('enables publishing when everything is filled and Mollie is COMPLETED', () => {
    renderWith();

    expect(screen.getByText('confirm').closest('button')).toBeEnabled();
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
