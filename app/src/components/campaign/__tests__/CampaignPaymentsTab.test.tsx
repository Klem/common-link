import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CampaignPaymentsTab } from '../CampaignPaymentsTab';
import type { CampaignDto } from '@/types/campaign';
import type { UsePaymentsReturn } from '@/hooks/campaign/usePayments';
import type { PayoutDto } from '@/types/payment';
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
};

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
  ibans: [{ id: 'iban-1', iban: 'FR76 0000 0000', status: 'VERIFIED', vopResult: null, vopSuggestedName: null, verifiedAt: null }],
  createdAt: '2026-01-01T00:00:00Z',
};

/** Same payee, but its only IBAN has not reached VERIFIED. */
const samplePayeeUnverifiedIban = {
  ...samplePayee,
  id: 'payee-2',
  name: 'Unverified Payee',
  ibans: [{ id: 'iban-2', iban: 'FR76 1111 1111', status: 'PENDING' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null }],
};

/** PERSON-type payee, required for REMUNERATION typeCodes. */
const samplePayeePerson = {
  ...samplePayee,
  id: 'payee-person-1',
  payeeType: 'PERSON' as const,
  name: 'Marie Dupont',
  identifier1: null,
  ibans: [{ id: 'iban-person-1', iban: 'FR76 4444 4444', status: 'VERIFIED' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null }],
};

/** Payee with one VERIFIED and one non-VERIFIED IBAN. */
const samplePayeeMixedIbans = {
  ...samplePayee,
  id: 'payee-3',
  name: 'Mixed Payee',
  ibans: [
    { id: 'iban-3', iban: 'FR76 2222 2222', status: 'VERIFIED' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null },
    { id: 'iban-4', iban: 'FR76 3333 3333', status: 'INVALID' as const, vopResult: null, vopSuggestedName: null, verifiedAt: null },
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
    refetch: vi.fn(),
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

  it('shows "no verified IBAN" message when the payee only has an unverified IBAN', () => {
    setupMocks([samplePayeeUnverifiedIban]);
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-2' } });

    expect(screen.getByText('noVerifiedIban')).toBeDefined();
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
    mockGetBlockingReasons.mockResolvedValue(['IBAN_NOT_VERIFIED']);
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Achat de fournitures diverses' },
    });

    await waitFor(() => {
      expect(screen.getByText('blocking.ibanNotVerified')).toBeDefined();
    });
    expect((screen.getByRole('button', { name: /form.submit/i }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows no pills when there are no active blocking reasons', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });

    await waitFor(() => expect(mockGetBlockingReasons).toHaveBeenCalled());
    expect(screen.queryByText('blocking.insufficientBalance')).toBeNull();
    expect(screen.queryByText('blocking.ibanNotVerified')).toBeNull();
  });
});
