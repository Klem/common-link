import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CampaignPaymentsTab } from '../CampaignPaymentsTab';
import type { CampaignDto } from '@/types/campaign';

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

vi.mock('@/hooks/campaign/usePayments');
vi.mock('@/hooks/payee/usePayees');
vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => vi.fn(),
}));

import { usePayments } from '@/hooks/campaign/usePayments';
import { usePayees } from '@/hooks/payee/usePayees';

const mockUsePayments = usePayments as ReturnType<typeof vi.fn>;
const mockUsePayees = usePayees as ReturnType<typeof vi.fn>;

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

const samplePayout = {
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

const samplePayee = {
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

const defaultSubmit = vi.fn().mockResolvedValue(samplePayout);

function setupMocks(overrides: Partial<ReturnType<typeof usePayments>> = {}) {
  mockUsePayments.mockReturnValue({
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
  });
  mockUsePayees.mockReturnValue({
    payees: [samplePayee],
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
});

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('CampaignPaymentsTab', () => {
  it('renders stats bar with summary data', () => {
    setupMocks({ summary: sampleSummary });
    render(<CampaignPaymentsTab campaign={campaign} />);

    expect(screen.getByText('stats.availableBalance')).toBeDefined();
    expect(screen.getByText('stats.paid')).toBeDefined();
    expect(screen.getByText('stats.pending')).toBeDefined();
    expect(screen.getByText('stats.transactions')).toBeDefined();
    expect(screen.getByText('stats.confirmed')).toBeDefined();
  });

  it('submit button is disabled when form is empty', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} />);

    const btn = screen.getByRole('button', { name: /form.submit/i });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
  });

  it('submit button enables when all required fields are valid', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} />);

    // Select payee (auto-fills IBAN since only one)
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'payee-1' } });
    // Select typeCode
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: '60-mat' } });
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
    render(<CampaignPaymentsTab campaign={campaign} />);

    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: '60-mat' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Achat de fournitures diverses' },
    });

    const btn = screen.getByRole('button', { name: /form.submit/i });
    fireEvent.click(btn);

    await waitFor(() => {
      expect(screen.getByText('confirm.title')).toBeDefined();
    });
  });

  it('confirming the dialog calls submit and resets form', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} />);

    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: '60-mat' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Achat de fournitures diverses' },
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
    setupMocks({ payouts: [samplePayout], summary: sampleSummary });
    render(<CampaignPaymentsTab campaign={campaign} />);

    // payeeName appears in both the select option and the history row
    expect(screen.getAllByText('ACME Corp').length).toBeGreaterThan(0);
    expect(screen.queryByText('history.empty')).toBeNull();
  });

  it('shows empty state when no payouts', () => {
    setupMocks({ payouts: [] });
    render(<CampaignPaymentsTab campaign={campaign} />);

    expect(screen.getByText('history.empty')).toBeDefined();
  });

  it('shows loading spinner while fetching', () => {
    setupMocks({ isLoading: true });
    render(<CampaignPaymentsTab campaign={campaign} />);

    // spinner present (animate-spin div)
    const spinner = document.querySelector('.animate-spin');
    expect(spinner).toBeTruthy();
  });

  it('shows error state', () => {
    setupMocks({ error: 'common.errors.serverError' });
    render(<CampaignPaymentsTab campaign={campaign} />);

    expect(screen.getByText('common.errors.serverError')).toBeDefined();
  });

  it('typeCode=64-rem sets kind to REMUNERATION', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} />);

    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'payee-1' } });
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: '64-rem' } });
    fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: '1000' } });
    fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'Salaire mensuel développeur' },
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
});
