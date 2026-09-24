import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CampaignPaymentsTab } from '../CampaignPaymentsTab';
import type { CampaignDto } from '@/types/campaign';
import type { UsePaymentsReturn } from '@/hooks/campaign/usePayments';
import type { PayoutDto } from '@/types/payment';
import { PayoutErrorCode } from '@/types/payment';
import type { PayeeDto } from '@/types/payee';

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, unknown>) => {
    if (params) return `${key}:${JSON.stringify(params)}`;
    return key;
  },
}));

vi.mock('@/hooks/payee/usePayees');
vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => vi.fn(),
}));
vi.mock('@/lib/api/payment', () => ({
  getBlockingReasons: vi.fn().mockResolvedValue([]),
}));

import { usePayees } from '@/hooks/payee/usePayees';
import { getBlockingReasons } from '@/lib/api/payment';

const mockUsePayees = usePayees as ReturnType<typeof vi.fn>;
const mockGetBlockingReasons = getBlockingReasons as ReturnType<typeof vi.fn>;

// ── Fixtures ──────────────────────────────────────────────────────────────────

const campaign: CampaignDto = {
  id: 'camp-1',
  name: 'Test Campaign',
  emoji: '🌍',
  description: 'desc',
  goal: 10000,
  raised: 5000,
  status: 'LIVE',
  startDate: null,
  endDate: null,
  milestones: [],
  budgetSections: [],
};

const sampleSummary = {
  confirmedAmount: 500,
  confirmedCount: 2,
  pendingAmount: 100,
  txTotal: 3,
  txConfirmed: 2,
  availableBalance: 4400,
  paymentsEnabled: true,
};

/**
 * Backend reports payouts as not issuable — production running Bridge in demo mode, where a
 * payout would be simulated rather than executed. Demo mode alone does not disable the button:
 * local and staging stay enabled.
 */
const paymentsDisabledSummary = { ...sampleSummary, paymentsEnabled: false };

const samplePayout: PayoutDto = {
  id: 'payout-1',
  campaignId: 'camp-1',
  payeeId: 'payee-1',
  payeeName: 'ACME Corp',
  payeeIbanId: 'iban-1',
  ibanValue: 'FR76 0000 0000 0000 0000',
  amount: 250,
  kind: 'EXPENSE',
  typeCode: '60-mat',
  label: 'Achat fournitures',
  status: 'CONFIRMED',
  createdAt: '2026-06-01T10:00:00Z',
  confirmedAt: '2026-06-01T10:05:00Z',
  onchainJobId: null,
  bridgeStatus: 'ACSC',
  bridgeLastErrorCode: null,
  bridgeCheckoutUrl: null,
};

/** Transfer authorised at the bank but not settled yet — still PENDING for accounting. */
const inFlightPayout: PayoutDto = {
  ...samplePayout,
  id: 'payout-2',
  status: 'PENDING',
  confirmedAt: null,
  bridgeStatus: 'PDNG',
};

/** Confirmed order still waiting for the association to authorise it at its own bank. */
const awaitingBankPayout: PayoutDto = {
  ...samplePayout,
  id: 'payout-3',
  status: 'PENDING',
  confirmedAt: null,
  bridgeStatus: 'CREA',
  bridgeCheckoutUrl: 'https://pay.bridgeapi.io/link/abc',
};

/**
 * Released after an attempt that moved no money — a Bridge refusal, or a link that died unused.
 * Still PENDING and re-issuable, with no authorisation URL because the old one is dead.
 */
const releasedPayout: PayoutDto = {
  ...samplePayout,
  id: 'payout-4',
  status: 'PENDING',
  confirmedAt: null,
  bridgeStatus: null,
  bridgeCheckoutUrl: null,
  bridgeLastErrorCode: PayoutErrorCode.LINK_EXPIRED,
};

const samplePayee: PayeeDto = {
  id: 'payee-1',
  payeeType: 'COMPANY' as const,
  name: 'ACME Corp',
  identifier1: '123456789',
  identifier2: null,
  activityCode: null,
  category: null,
  city: 'Paris',
  postalCode: '75001',
  active: true,
  hasPayouts: false,
  ibans: [{ id: 'iban-1', iban: 'FR76 0000 0000', status: 'VERIFIED', vopResult: null, vopSuggestedName: null, verifiedAt: null, active: true }],
  createdAt: '2026-01-01T00:00:00Z',
};

/** Same payee, but its only IBAN has not reached VERIFIED. */
const samplePayeeUnverifiedIban = {
  ...samplePayee,
  id: 'payee-2',
  name: 'Unverified Payee',
  ibans: [{ id: 'iban-2', iban: 'FR76 1111 1111', status: 'PENDING' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null, active: true }],
};

/** PERSON-type payee, required for REMUNERATION typeCodes. */
const samplePayeePerson = {
  ...samplePayee,
  id: 'payee-person-1',
  payeeType: 'PERSON' as const,
  name: 'Marie Dupont',
  identifier1: null,
  ibans: [{ id: 'iban-person-1', iban: 'FR76 4444 4444', status: 'VERIFIED' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null, active: true }],
};

/** Payee whose only IBAN is VERIFIED but disabled. */
const samplePayeeDisabledIban = {
  ...samplePayee,
  id: 'payee-4',
  name: 'Disabled Iban Payee',
  ibans: [{ id: 'iban-5', iban: 'FR76 5555 5555', status: 'VERIFIED' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null, active: false }],
};

/** Payee with one VERIFIED and one non-VERIFIED IBAN. */
const samplePayeeMixedIbans = {
  ...samplePayee,
  id: 'payee-3',
  name: 'Mixed Payee',
  ibans: [
    { id: 'iban-3', iban: 'FR76 2222 2222', status: 'VERIFIED' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null, active: true },
    { id: 'iban-4', iban: 'FR76 3333 3333', status: 'INVALID' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null, active: true },
  ],
};

const defaultSubmit = vi.fn().mockResolvedValue(samplePayout);

function setupPayments(overrides: Partial<UsePaymentsReturn> = {}): UsePaymentsReturn {
  return {
    payouts: [],
    summary: null,
    isLoading: false,
    isSaving: false,
    error: null,
    page: 0,
    totalPages: 0,
    setPage: vi.fn(),
    submit: defaultSubmit,
    retry: vi.fn(),
    refetch: vi.fn(),
    awaitingReturnPayoutId: null,
    ...overrides,
  };
}

function setupMocks(payeesOverride: PayeeDto[] = [samplePayee]) {
  mockUsePayees.mockReturnValue({
    payees: payeesOverride,
    isLoading: false,
    error: null,
    fetchPayees: vi.fn(),
    addPayeeIban: vi.fn(),
    removePayeeIban: vi.fn(),
    removePayee: vi.fn(),
    refreshPayee: vi.fn(),
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  mockGetBlockingReasons.mockResolvedValue([]);
});

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('CampaignPaymentsTab', () => {
  it('renders stats bar with summary data', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ summary: sampleSummary })} />);

    expect(screen.getByText('stats.availableBalance')).toBeDefined();
    expect(screen.getByText('stats.paid')).toBeDefined();
    expect(screen.getByText('stats.pending')).toBeDefined();
    expect(screen.getByText('stats.transactions')).toBeDefined();
    expect(screen.getByText('stats.confirmed')).toBeDefined();
  });

  it('submit button is disabled when form is empty', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    const btn = screen.getByRole('button', { name: /form.submit/i });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
  });

  it('submit button enables when all required fields are valid', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    // Select payee (auto-fills IBAN since only one VERIFIED)
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    // Select typeCode
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
    // Enter amount
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    // Enter label (min 6 chars)
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Achat de fournitures diverses' },
    });

    await waitFor(() => {
      const btn = screen.getByRole('button', { name: /form.submit/i });
      expect((btn as HTMLButtonElement).disabled).toBe(false);
    });
  });

  it('submit button stays disabled with an explanatory tooltip when payments are not enabled', async () => {
    setupMocks();
    render(
      <CampaignPaymentsTab campaign={campaign} payments={setupPayments({ summary: paymentsDisabledSummary })} />,
    );

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Achat de fournitures diverses' },
    });

    await waitFor(() => {
      expect(mockGetBlockingReasons).toHaveBeenCalled();
    });
    const btn = screen.getByRole('button', { name: /form.submit/i });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
    expect(btn.parentElement?.getAttribute('title')).toBe('form.paymentsDisabled');
  });

  it('clicking submit shows the confirm dialog', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Achat de fournitures diverses' },
    });

    await waitFor(() => {
      expect((screen.getByRole('button', { name: /form.submit/i }) as HTMLButtonElement).disabled).toBe(false);
    });
    fireEvent.click(screen.getByRole('button', { name: /form.submit/i }));

    await waitFor(() => {
      expect(screen.getByText('confirm.title')).toBeDefined();
    });
  });

  it('confirming the dialog calls submit and resets form', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Achat de fournitures diverses' },
    });

    await waitFor(() => {
      expect((screen.getByRole('button', { name: /form.submit/i }) as HTMLButtonElement).disabled).toBe(false);
    });
    fireEvent.click(screen.getByRole('button', { name: /form.submit/i }));

    await waitFor(() => screen.getByText('confirm.title'));
    fireEvent.click(screen.getByText('confirm.submit'));

    await waitFor(() => {
      expect(defaultSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          payeeId: 'payee-1',
          payeeIbanId: 'iban-1',
          amount: 100,
          kind: 'EXPENSE',
          typeCode: '60-mat',
          label: 'Achat de fournitures diverses',
        }),
      );
    });
  });

  it('shows payment history list', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout], summary: sampleSummary })} />);

    // payeeName appears in both the select option and the history row
    expect(screen.getAllByText('ACME Corp').length).toBeGreaterThan(0);
    expect(screen.queryByText('history.empty')).toBeNull();
  });

  it('shows empty state when no payouts', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [] })} />);

    expect(screen.getByText('history.empty')).toBeDefined();
  });

  it('shows loading spinner while fetching', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ isLoading: true })} />);

    // spinner present (animate-spin div)
    const spinner = document.querySelector('.animate-spin');
    expect(spinner).toBeTruthy();
  });

  it('shows error state', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ error: 'common.errors.serverError' })} />);

    expect(screen.getByText('common.errors.serverError')).toBeDefined();
  });

  it('typeCode=64-rem sets kind to REMUNERATION', async () => {
    setupMocks([samplePayeePerson]);
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    // REMUNERATION typeCodes only list PERSON payees — select type first so the payee list updates
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '64-rem' } });
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-person-1' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '1000' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Salaire mensuel développeur' },
    });

    await waitFor(() => {
      expect((screen.getByRole('button', { name: /form.submit/i }) as HTMLButtonElement).disabled).toBe(false);
    });
    fireEvent.click(screen.getByRole('button', { name: /form.submit/i }));
    await waitFor(() => screen.getByText('confirm.title'));
    fireEvent.click(screen.getByText('confirm.submit'));

    await waitFor(() => {
      expect(defaultSubmit).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'REMUNERATION', typeCode: '64-rem' }),
      );
    });
  });

  // ── Lot 1: verified-IBAN-only selector ─────────────────────────────────────

  it('excludes a payee with no VERIFIED IBAN from the payee dropdown entirely', () => {
    setupMocks([samplePayeeUnverifiedIban]);
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    const payeeSelect = screen.getAllByRole('combobox')[1];
    expect(screen.queryByRole('option', { name: 'Unverified Payee' })).toBeNull();
    fireEvent.change(payeeSelect, { target: { value: 'payee-2' } });
    expect((payeeSelect as HTMLSelectElement).value).toBe('');
  });

  it('excludes a payee whose only VERIFIED IBAN is disabled from the payee dropdown', () => {
    setupMocks([samplePayeeDisabledIban]);
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    expect(screen.queryByRole('option', { name: 'Disabled Iban Payee' })).toBeNull();
  });

  it('does not auto-select and excludes non-VERIFIED IBANs from the multi-IBAN selector', () => {
    setupMocks([samplePayeeMixedIbans]);
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-3' } });

    // Only one VERIFIED IBAN exists among the two -> auto-selected, no dropdown shown, invalid one not offered
    expect(screen.queryByText('FR76 3333 3333')).toBeNull();
    expect(screen.getByText('FR76 2222 2222')).toBeDefined();
  });

  // ── Lot 1: payment blocking reason pills ───────────────────────────────────

  it('renders a pill and disables submit when a blocking reason is active', async () => {
    mockGetBlockingReasons.mockResolvedValue(['INSUFFICIENT_BALANCE']);
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Achat de fournitures diverses' },
    });

    await waitFor(() => {
      expect(screen.getByText('blocking.insufficientBalance')).toBeDefined();
    });
    expect((screen.getByRole('button', { name: /form.submit/i }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('renders the descriptionTooShort pill client-side, without waiting on the blocking-reasons API', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'dfgdfg' },
    });

    expect(screen.getByText('blocking.descriptionTooShort')).toBeDefined();
    expect((screen.getByRole('button', { name: /form.submit/i }) as HTMLButtonElement).disabled).toBe(true);
  });

  // ── Bridge transfer state ──────────────────────────────────────────────────

  it('marks a settled payout as confirmed', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />);

    expect(document.querySelector('.pay-chip.confirmed')).toBeTruthy();
    expect(document.querySelector('.pay-chip.pending')).toBeNull();
  });

  it('shows an authorised but unsettled transfer as in-transit, not settled', () => {
    // The bank has the order but the beneficiary is credited days later — a check mark here would
    // claim the money arrived.
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [inFlightPayout] })} />);

    const chip = document.querySelector('.pay-chip.pending');
    expect(chip).toBeTruthy();
    expect(chip?.getAttribute('title')).toBe('history.inTransit');
    expect(document.querySelector('.pay-chip.confirmed')).toBeNull();
  });

  it('offers a bank-authorisation link while the transfer awaits the association', () => {
    // The association is the debtor: an initiation it never authorised moves no money, so the tab
    // must let it resume instead of stranding the payout.
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [awaitingBankPayout] })} />);

    const link = screen.getByRole('link', { name: 'history.authorise' });
    expect(link.getAttribute('href')).toBe('https://pay.bridgeapi.io/link/abc');
  });

  it('states the wait instead of the link right after the return from the bank', () => {
    // Coming back from the bank the payout is still CREA only because Bridge has not finished
    // notifying — CREA, ACTC and PDNG landed within 24 seconds of each other on 2026-09-22. The
    // link here would re-open one being consumed, and re-clicking it is the natural reflex.
    setupMocks();
    render(
      <CampaignPaymentsTab
        campaign={campaign}
        payments={setupPayments({
          payouts: [awaitingBankPayout],
          awaitingReturnPayoutId: awaitingBankPayout.id,
        })}
      />,
    );

    expect(screen.queryByRole('link', { name: 'history.authorise' })).toBeNull();
    expect(screen.getByText('history.awaitingBank')).toBeTruthy();
  });

  it('offers a retry on a payout whose last attempt failed, without re-creating it', async () => {
    // Nothing was debited, so the payout was deliberately left retryable rather than failed. With
    // no action that only meant something in the database: the association re-created the payout
    // instead, which is how four identical rows appeared from one payment on 2026-09-23.
    setupMocks();
    const retry = vi.fn().mockResolvedValue(samplePayout);
    render(
      <CampaignPaymentsTab
        campaign={campaign}
        payments={setupPayments({ payouts: [releasedPayout], retry })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'history.retry' }));

    await waitFor(() => expect(retry).toHaveBeenCalledWith(releasedPayout.id));
    // Re-issuing, not re-creating: the accounting row the association filled in is reused.
    expect(defaultSubmit).not.toHaveBeenCalled();
  });

  it('offers no retry while the transfer is still in flight', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [inFlightPayout] })} />);

    expect(screen.queryByRole('button', { name: 'history.retry' })).toBeNull();
  });

  it('offers no authorisation link once the transfer is settled', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />);

    expect(screen.queryByRole('link', { name: 'history.authorise' })).toBeNull();
  });

  it('surfaces the bank rejection reason on a failed payout', () => {
    setupMocks();
    const failed: PayoutDto = {
      ...samplePayout,
      status: 'FAILED',
      bridgeStatus: 'RJCT',
      bridgeLastErrorCode: PayoutErrorCode.AM04,
    };
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [failed] })} />);

    // Translated from the stable code, never the stored string: that one is Bridge's bare ISO
    // reason or one of our English messages, and it used to land in this tooltip verbatim.
    expect(document.querySelector('.pay-chip.failed')?.getAttribute('title'))
      .toBe('history.errorCode.AM04');
  });

  it('distinguishes a payout whose last transfer attempt failed from one never attempted', () => {
    // The backend releases a failed initiation back to PENDING on purpose — FAILED is terminal and
    // nothing was debited. Without a distinct chip the association sees the same hourglass as a
    // payout it has not submitted yet, and never learns it has to retry.
    setupMocks();
    const releasedAfterFailure: PayoutDto = {
      ...samplePayout,
      status: 'PENDING',
      confirmedAt: null,
      bridgeStatus: null,
      bridgeLastErrorCode: PayoutErrorCode.DESTINATION_UNVERIFIED,
    };
    render(
      <CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [releasedAfterFailure] })} />,
    );

    const chip = document.querySelector('.pay-chip.attention');
    expect(chip).toBeTruthy();
    // The tooltip must name the actual cause, not a generic "something went wrong" — here the
    // destination read-back guard, which is a control refusing rather than a bank being busy.
    expect(chip?.getAttribute('title')).toBe('history.errorCode.DESTINATION_UNVERIFIED');
    expect(document.querySelector('.pay-chip.confirmed')).toBeNull();
  });

  it('keeps the plain pending chip for a payout never submitted to Bridge', () => {
    setupMocks();
    const neverAttempted: PayoutDto = {
      ...samplePayout,
      status: 'PENDING',
      confirmedAt: null,
      bridgeStatus: null,
      bridgeLastErrorCode: null,
    };
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [neverAttempted] })} />);

    expect(document.querySelector('.pay-chip.pending')).toBeTruthy();
    expect(document.querySelector('.pay-chip.attention')).toBeNull();
  });

  it('shows no pills when there are no active blocking reasons', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });

    await waitFor(() => expect(mockGetBlockingReasons).toHaveBeenCalled());
    expect(screen.queryByText('blocking.insufficientBalance')).toBeNull();
    expect(screen.queryByText('blocking.descriptionTooShort')).toBeNull();
  });
});
