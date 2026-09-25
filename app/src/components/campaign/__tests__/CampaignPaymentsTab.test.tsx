import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act, within } from '@testing-library/react';
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
  bridgePaymentTransactionId: 'bridge-tx-1',
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

/**
 * Scopes queries to the issuance form, left column of the tab.
 *
 * Scoping matters: the journal below has selects of its own, so an index into every combobox on
 * the page would reach the wrong one.
 */
function issueForm() {
  return within(document.querySelector('.pay-form-grid .cm-card') as HTMLElement);
}

/** Fills the form with a valid payment. */
function fillValidForm(form: ReturnType<typeof issueForm>) {
  // Select payee (auto-fills IBAN since only one VERIFIED)
  fireEvent.change(form.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
  // Select typeCode
  fireEvent.change(form.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
  fireEvent.change(form.getByPlaceholderText('0,00'), { target: { value: '100' } });
  fireEvent.change(form.getByPlaceholderText('form.labelPlaceholder'), {
    target: { value: 'Achat de fournitures diverses' },
  });
}

/**
 * Gives the header cells a measurable width.
 *
 * jsdom lays nothing out, so every `getBoundingClientRect` returns zero and the resize arithmetic
 * has nothing to divide by. These are the numbers the component would read from a real layout.
 */
function stubHeaderWidths(container: HTMLElement, widths: number[]) {
  container.querySelectorAll('.pj-table thead th').forEach((cell, i) => {
    (cell as HTMLElement).getBoundingClientRect = () => ({ width: widths[i] } as DOMRect);
  });
}

/** The row the journal renders for a payout, by its beneficiary cell. */
function journalRows() {
  return [...document.querySelectorAll('tr.pj-row')];
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

  // ── Issuance panel ─────────────────────────────────────────────────────────

  it('keeps the issuance form beside the breakdown, above the journal', () => {
    setupMocks();
    const { container } = render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);

    const grid = container.querySelector('.pay-form-grid')!;
    expect(grid.querySelectorAll(':scope > .cm-card')).toHaveLength(2);
    expect(within(grid as HTMLElement).getByRole('button', { name: /form.submit/i })).toBeDefined();
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
    fillValidForm(issueForm());

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
    fillValidForm(issueForm());

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
    fillValidForm(issueForm());

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
    fillValidForm(issueForm());

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
    // The form empties itself rather than inviting a second, identical payment.
    await waitFor(() => expect((issueForm().getByPlaceholderText('0,00') as HTMLInputElement).value).toBe(''));
  });

  it('typeCode=64-rem sets kind to REMUNERATION', async () => {
    setupMocks([samplePayeePerson]);
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);
    const form = issueForm();

    // REMUNERATION typeCodes only list PERSON payees — select type first so the payee list updates
    fireEvent.change(form.getAllByRole('combobox')[0], { target: { value: '64-rem' } });
    fireEvent.change(form.getAllByRole('combobox')[1], { target: { value: 'payee-person-1' } });
    fireEvent.change(form.getByPlaceholderText('0,00'), { target: { value: '1000' } });
    fireEvent.change(form.getByPlaceholderText('form.labelPlaceholder'), {
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
    const form = issueForm();

    const payeeSelect = form.getAllByRole('combobox')[1];
    expect(form.queryByRole('option', { name: 'Unverified Payee' })).toBeNull();
    fireEvent.change(payeeSelect, { target: { value: 'payee-2' } });
    expect((payeeSelect as HTMLSelectElement).value).toBe('');
  });

  it('excludes a payee whose only VERIFIED IBAN is disabled from the payee dropdown', () => {
    setupMocks([samplePayeeDisabledIban]);
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);
    const form = issueForm();

    expect(form.queryByRole('option', { name: 'Disabled Iban Payee' })).toBeNull();
  });

  it('does not auto-select and excludes non-VERIFIED IBANs from the multi-IBAN selector', () => {
    setupMocks([samplePayeeMixedIbans]);
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);
    const form = issueForm();

    fireEvent.change(form.getAllByRole('combobox')[1], { target: { value: 'payee-3' } });

    // Only one VERIFIED IBAN exists among the two -> auto-selected, no dropdown shown, invalid one not offered
    expect(form.queryByText('FR76 3333 3333')).toBeNull();
    expect(form.getByText('FR76 2222 2222')).toBeDefined();
  });

  // ── Lot 1: payment blocking reason pills ───────────────────────────────────

  it('renders a pill and disables submit when a blocking reason is active', async () => {
    mockGetBlockingReasons.mockResolvedValue(['INSUFFICIENT_BALANCE']);
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);
    fillValidForm(issueForm());

    await waitFor(() => {
      expect(screen.getByText('blocking.insufficientBalance')).toBeDefined();
    });
    expect((screen.getByRole('button', { name: /form.submit/i }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('renders the descriptionTooShort pill client-side, without waiting on the blocking-reasons API', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);
    const form = issueForm();

    fireEvent.change(form.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(form.getAllByRole('combobox')[0], { target: { value: '60-mat' } });
    fireEvent.change(form.getByPlaceholderText('0,00'), { target: { value: '100' } });
    fireEvent.change(form.getByPlaceholderText('form.labelPlaceholder'), {
      target: { value: 'dfgdfg' },
    });

    expect(screen.getByText('blocking.descriptionTooShort')).toBeDefined();
    expect((screen.getByRole('button', { name: /form.submit/i }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows no pills when there are no active blocking reasons', async () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments()} />);
    const form = issueForm();

    fireEvent.change(form.getAllByRole('combobox')[1], { target: { value: 'payee-1' } });
    fireEvent.change(form.getByPlaceholderText('0,00'), { target: { value: '100' } });

    await waitFor(() => expect(mockGetBlockingReasons).toHaveBeenCalled());
    expect(screen.queryByText('blocking.insufficientBalance')).toBeNull();
    expect(screen.queryByText('blocking.descriptionTooShort')).toBeNull();
  });

  // ── Journal: anatomy of a row ──────────────────────────────────────────────

  it('gives each row its own cell per information, reference included', () => {
    setupMocks();
    const { container } = render(
      <CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />,
    );

    const row = journalRows()[0];
    expect(row.querySelector('.pj-payee')?.textContent).toBe('ACME Corp');
    expect(row.querySelector('.pj-type')?.textContent).toBe('typeCodes.60-mat');
    expect(row.querySelector('.pj-code')?.textContent).toBe('60-mat');
    // The reference is Bridge's own transaction id — the payout's only identifier that also
    // exists outside CommonLink.
    expect(row.querySelector('.pj-ref')?.textContent).toBe('bridge-tx-1');
    // The amount carries no status colour — that is the badge's job alone.
    expect(row.querySelector('.pj-amount')?.getAttribute('style')).toBeNull();
    // The journal no longer stacks `.pay-row` flex lines; the breakdown card still does.
    expect(container.querySelector('.pj-card .pay-row')).toBeNull();
  });

  it('shows no reference at all on a payout no transfer was ever ordered for', () => {
    // Nothing is invented in its place: a made-up reference would not match anything Bridge or the
    // bank knows about.
    setupMocks();
    const payouts = [
      { ...samplePayout, id: 'with', bridgePaymentTransactionId: 'bridge-tx-9' },
      { ...releasedPayout, id: 'without', bridgePaymentTransactionId: null },
    ];
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    const refs = [...document.querySelectorAll('.pj-ref')].map((n) => n.textContent);
    expect(refs).toEqual(['bridge-tx-9']);
  });

  it('shows the translated label of a preset type code, in journal and breakdown', () => {
    setupMocks();
    const payout = { ...samplePayout, typeCode: '60-svc' };
    const { container } = render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [payout] })} />);

    expect(container.querySelector('.pj-type')?.textContent).toBe('typeCodes.60-svc');
    expect(container.querySelector('.breakdown-label')?.textContent).toBe('typeCodes.60-svc');
    // Lone category: the donut still draws its ring.
    expect(container.querySelector('path.donut-segment')).not.toBeNull();
  });

  it('shows a custom type code as typed', () => {
    setupMocks();
    const payout = { ...samplePayout, typeCode: 'Frais divers' };
    const { container } = render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [payout] })} />);

    expect(container.querySelector('.pj-type')?.textContent).toBe('Frais divers');
  });

  // ── Journal: breakdown card ────────────────────────────────────────────────

  it('lists each expense line under the donut, with its settled transactions in an expandable entry', () => {
    setupMocks();
    const payouts = [
      { ...samplePayout, id: 'a', typeCode: '60-svc', amount: 100, payeeName: 'Alpha' },
      { ...samplePayout, id: 'b', typeCode: '60-svc', amount: 50, payeeName: 'Beta' },
      { ...samplePayout, id: 'c', typeCode: '60-mat', amount: 400, payeeName: 'Gamma' },
      // Not settled: stays out of the breakdown.
      { ...inFlightPayout, id: 'd', typeCode: '60-svc', amount: 999, payeeName: 'Delta' },
    ];
    const { container } = render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    const items = container.querySelectorAll('details.breakdown-item');
    expect(items).toHaveLength(2);
    // Largest line first.
    expect(items[0].querySelector('.breakdown-label')?.textContent).toBe('typeCodes.60-mat');
    expect(items[1].querySelector('.breakdown-count')?.textContent).toBe('breakdown.count:{"count":2}');
    const names = [...items[1].querySelectorAll('.pay-row-name')].map((n) => n.textContent);
    expect(names).toEqual(['Alpha', 'Beta']);
  });

  it('keeps a single expense line expanded at a time', () => {
    setupMocks();
    const payouts = [
      { ...samplePayout, id: 'a', typeCode: '60-svc', amount: 100 },
      { ...samplePayout, id: 'c', typeCode: '60-mat', amount: 400 },
    ];
    const { container } = render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);
    const [first, second] = [...container.querySelectorAll<HTMLDetailsElement>('details.breakdown-item')];

    act(() => { first.open = true; fireEvent(first, new Event('toggle')); });
    expect(first.open).toBe(true);

    act(() => { second.open = true; fireEvent(second, new Event('toggle')); });
    expect(second.open).toBe(true);
    expect(first.open).toBe(false);
  });

  // ── Journal: list states ───────────────────────────────────────────────────

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

  // ── Journal: search, filters, sort, counters ───────────────────────────────

  it('lets the content size the columns until someone resizes one', () => {
    // Content-driven widths are what keep a short beneficiary from leaving a gulf beside it; the
    // moment a viewer moves an edge, measured shares take over and the layout stops re-deriving
    // them. jsdom lays nothing out, so the table here stays on the browser's own algorithm.
    setupMocks();
    const { container } = render(
      <CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />,
    );

    const table = container.querySelector('.pj-table')!;
    expect([...table.querySelectorAll('colgroup col')].map((c) => c.className)).toEqual([
      'pj-col-date', 'pj-col-payee', 'pj-col-type', 'pj-col-amount', 'pj-col-status', 'pj-col-action',
    ]);
    expect([...table.querySelectorAll('colgroup col')].every((c) => !c.getAttribute('style'))).toBe(true);
    expect(table.classList.contains('pj-table-sized')).toBe(false);

    // Five handles for six columns: nothing follows the last one, so its edge cannot be moved.
    expect(container.querySelectorAll('.pj-resizer')).toHaveLength(5);
  });

  it('moves width between two neighbours, never past the table', () => {
    // The whole point of shares that sum to 100%: a resize redistributes, it never widens the
    // table, so the journal can never grow a horizontal scrollbar.
    setupMocks();
    const { container } = render(
      <CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />,
    );
    stubHeaderWidths(container, [100, 200, 300, 100, 200, 100]);

    fireEvent.keyDown(container.querySelectorAll('.pj-resizer')[0], { key: 'ArrowRight' });

    const widths = [...container.querySelectorAll('.pj-table colgroup col')]
      .map((c) => parseFloat((c as HTMLElement).style.width));
    // 16px of a 1000px table: the date column gains 1.6 points, the beneficiary loses them.
    expect(widths[0]).toBeCloseTo(11.6, 5);
    expect(widths[1]).toBeCloseTo(18.4, 5);
    expect(widths.reduce((a, b) => a + b, 0)).toBeCloseTo(100, 5);
  });

  it('refuses to squeeze a neighbour below its minimum', () => {
    setupMocks();
    const { container } = render(
      <CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />,
    );
    // The beneficiary column is already at the 64px floor, so it can give nothing away.
    stubHeaderWidths(container, [100, 64, 300, 100, 200, 236]);

    fireEvent.keyDown(container.querySelectorAll('.pj-resizer')[0], { key: 'ArrowRight' });

    const widths = [...container.querySelectorAll('.pj-table colgroup col')]
      .map((c) => parseFloat((c as HTMLElement).style.width));
    expect(widths[0]).toBeCloseTo(10, 5);
    expect(widths[1]).toBeCloseTo(6.4, 5);
    expect(widths.reduce((a, b) => a + b, 0)).toBeCloseTo(100, 5);
  });

  it('searches across beneficiary, account line and reference', () => {
    setupMocks();
    const payouts = [
      { ...samplePayout, id: 'a', payeeName: 'Scale That' },
      { ...samplePayout, id: 'b', payeeName: 'Autre Fournisseur', typeCode: '65-ges' },
    ];
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'scale' } });
    expect(journalRows()).toHaveLength(1);
    expect(journalRows()[0].querySelector('.pj-payee')?.textContent).toBe('Scale That');

    // The reference is what the association reads off its bank statement, so it must match too.
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'bridge-tx-1' } });
    expect(journalRows()).toHaveLength(2);
  });

  it('counts each quick filter and narrows the list to it', () => {
    setupMocks();
    const payouts = [
      samplePayout,
      inFlightPayout,
      releasedPayout,
      { ...samplePayout, id: 'f', status: 'FAILED' as const, bridgeStatus: 'RJCT' as const, bridgeLastErrorCode: PayoutErrorCode.AM04 },
    ];
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    const pill = (name: string) => screen.getByRole('button', { name: new RegExp(`^${name}`) });
    expect(pill('journal.pills.ALL').textContent).toContain('4');
    // Only the released payout asks for a click here: a terminal refusal offers nothing.
    expect(pill('journal.pills.TODO').textContent).toContain('1');
    expect(pill('journal.pills.FAILED').textContent).toContain('1');
    expect(pill('journal.pills.DONE').textContent).toContain('1');

    fireEvent.click(pill('journal.pills.FAILED'));
    expect(journalRows()).toHaveLength(1);
    expect(screen.getByText('state.FAILED')).toBeDefined();
  });

  it('reaches refused payouts in one click whatever the page shows', () => {
    setupMocks();
    // Forty rows: a refusal in fortieth position is invisible in a chronological feed.
    const payouts: PayoutDto[] = Array.from({ length: 40 }, (_, i) => ({
      ...samplePayout,
      id: `p-${i}`,
      createdAt: `2026-06-${String((i % 28) + 1).padStart(2, '0')}T10:00:00Z`,
    }));
    payouts[39] = { ...payouts[39], status: 'FAILED', bridgeStatus: 'RJCT', bridgeLastErrorCode: PayoutErrorCode.AM04 };
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    // Default page size hides it.
    expect(journalRows()).toHaveLength(25);
    fireEvent.click(screen.getByRole('button', { name: /^journal\.pills\.FAILED/ }));
    expect(journalRows()).toHaveLength(1);
  });

  it('recomputes the displayed counter and total on every filter', () => {
    setupMocks();
    const payouts = [
      { ...samplePayout, id: 'a', amount: 100 },
      { ...inFlightPayout, id: 'b', amount: 50 },
    ];
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    expect(screen.getByText(/journal\.counter:\{"count":2/)).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: /^journal\.pills\.DONE/ }));
    expect(screen.getByText(/journal\.counter:\{"count":1/)).toBeDefined();
  });

  it('sorts by amount on a header button, and flips direction on a second click', () => {
    setupMocks();
    const payouts = [
      { ...samplePayout, id: 'a', amount: 100 },
      { ...samplePayout, id: 'b', amount: 900 },
    ];
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    const amounts = () => journalRows().map((r) => r.querySelector('.pj-amount')?.textContent);
    fireEvent.click(screen.getByRole('button', { name: /journal\.col\.amount/ }));
    expect(amounts()[0]).toContain('900');
    fireEvent.click(screen.getByRole('button', { name: /journal\.col\.amount/ }));
    expect(amounts()[0]).toContain('100');
  });

  it('sorts by date descending by default', () => {
    setupMocks();
    const payouts = [
      { ...samplePayout, id: 'old', amount: 100, createdAt: '2026-06-01T10:00:00Z' },
      { ...samplePayout, id: 'new', amount: 900, createdAt: '2026-07-01T10:00:00Z' },
    ];
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    const header = screen.getByRole('button', { name: /journal\.col\.date/ }).closest('th');
    expect(header?.getAttribute('aria-sort')).toBe('descending');
    expect(journalRows()[0].querySelector('.pj-amount')?.textContent).toContain('900');
  });

  it('dates an operation to the second, not to the day', () => {
    // Two transfers to the same beneficiary for the same amount on the same day are told apart by
    // nothing else, and a statement is reconciled on the instant.
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />);

    expect(journalRows()[0].querySelector('.pj-date')?.textContent)
      .toMatch(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}:\d{2}$/);
  });

  it('explains an empty result and offers a way out', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />);

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'introuvable' } });
    expect(journalRows()).toHaveLength(0);
    expect(screen.getByText('journal.noMatch.title')).toBeDefined();
    expect(screen.getByText('journal.noMatch.hint')).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: 'journal.reset' }));
    expect(journalRows()).toHaveLength(1);
  });

  // ── Journal: status badges ─────────────────────────────────────────────────

  it('names every state in words, never in an icon alone', () => {
    setupMocks();
    const payouts = [samplePayout, inFlightPayout, awaitingBankPayout, releasedPayout];
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts })} />);

    expect(screen.getByText('state.CONFIRMED')).toBeDefined();
    expect(screen.getByText('state.AUTHORISED')).toBeDefined();
    expect(screen.getByText('state.AWAITING_AUTHORISATION')).toBeDefined();
    expect(screen.getByText('state.RETRYABLE')).toBeDefined();
  });

  it('marks a settled payout as executed', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />);

    expect(screen.getByText('state.CONFIRMED')).toBeDefined();
    expect(screen.queryByText('state.AWAITING_AUTHORISATION')).toBeNull();
  });

  it('shows an authorised but unsettled transfer as authorised, not settled', () => {
    // The bank has the order but the beneficiary is credited days later — "executed" here would
    // claim the money arrived.
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [inFlightPayout] })} />);

    expect(screen.getByText('state.AUTHORISED')).toBeDefined();
    expect(screen.queryByText('state.CONFIRMED')).toBeNull();
  });

  it('keeps the plain pending state for a payout never submitted to Bridge', () => {
    setupMocks();
    const neverAttempted: PayoutDto = {
      ...samplePayout,
      status: 'PENDING',
      confirmedAt: null,
      bridgeStatus: null,
      bridgeLastErrorCode: null,
    };
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [neverAttempted] })} />);

    expect(screen.getByText('state.PENDING')).toBeDefined();
    expect(screen.queryByText('state.RETRYABLE')).toBeNull();
  });

  it('distinguishes a payout whose last transfer attempt failed from one never attempted', () => {
    // The backend releases a failed initiation back to PENDING on purpose — FAILED is terminal and
    // nothing was debited. Without a distinct state the association sees the same row as a payout
    // it has not submitted yet, and never learns it has to retry.
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

    expect(screen.getByText('state.RETRYABLE')).toBeDefined();
    // The row must name the actual cause, not a generic "something went wrong" — here the
    // destination read-back guard, which is a control refusing rather than a bank being busy.
    expect(document.querySelector('.pj-reason')?.textContent)
      .toBe('history.errorCode.DESTINATION_UNVERIFIED');
    expect(screen.queryByText('state.CONFIRMED')).toBeNull();
  });

  it('surfaces the bank rejection reason on the row of a failed payout', () => {
    setupMocks();
    const failed: PayoutDto = {
      ...samplePayout,
      status: 'FAILED',
      bridgeStatus: 'RJCT',
      bridgeLastErrorCode: PayoutErrorCode.AM04,
    };
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [failed] })} />);

    // Translated from the stable code, never the stored string: that one is Bridge's bare ISO
    // reason or one of our English messages, and it used to land in a tooltip verbatim.
    expect(screen.getByText('state.FAILED')).toBeDefined();
    expect(document.querySelector('.pj-reason')?.textContent).toBe('history.errorCode.AM04');
  });

  it('never captions a settled transfer with a stale failure', () => {
    // An error code left over from an attempt that was later re-issued must not read as "solde
    // insuffisant" under a payment the beneficiary has been credited for.
    setupMocks();
    const settledAfterRetry: PayoutDto = { ...samplePayout, bridgeLastErrorCode: PayoutErrorCode.AM04 };
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [settledAfterRetry] })} />);

    expect(screen.getByText('state.CONFIRMED')).toBeDefined();
    expect(document.querySelector('.pj-reason')).toBeNull();
    fireEvent.click(journalRows()[0]);
    expect(document.querySelector('.pd-reason')).toBeNull();
  });

  // ── Journal: row actions ───────────────────────────────────────────────────

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

  it('does not open the detail panel when the row action is clicked', () => {
    // Opening a panel and ordering a real bank transfer must not be the same gesture.
    setupMocks();
    const retry = vi.fn().mockResolvedValue(samplePayout);
    render(
      <CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [releasedPayout], retry })} />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'history.retry' }));
    expect(document.querySelector('.side-panel')).toBeNull();
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

  // ── Detail panel ───────────────────────────────────────────────────────────

  it('opens the detail panel on a row click, with what the six columns cannot hold', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />);

    fireEvent.click(journalRows()[0]);

    const panel = within(document.querySelector('.side-panel') as HTMLElement);
    expect(panel.getByText('detail.title')).toBeDefined();
    expect(panel.getByText('FR76 0000 0000 0000 0000')).toBeDefined();
    expect(panel.getByText('detail.createdAt')).toBeDefined();
    // The payment's free-text justification stays out of the new screen entirely.
    expect(panel.queryByText('Achat fournitures')).toBeNull();
  });

  it('gives the full refusal sentence in the detail panel of a failed payout', () => {
    setupMocks();
    const failed: PayoutDto = {
      ...samplePayout,
      status: 'FAILED',
      bridgeStatus: 'RJCT',
      bridgeLastErrorCode: PayoutErrorCode.AM04,
    };
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [failed] })} />);

    fireEvent.click(journalRows()[0]);
    expect(document.querySelector('.pd-reason')?.textContent).toBe('history.errorCode.AM04');
  });

  it('opens the detail panel from the keyboard on a row whose action is a button', () => {
    // Those rows carry no chevron, and the panel is the only place the full refusal sentence is.
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [releasedPayout] })} />);

    const row = journalRows()[0];
    expect(row.querySelector('.pj-chevron')).toBeNull();
    fireEvent.keyDown(row, { key: 'Enter', target: row });

    expect(document.querySelector('.side-panel')).toBeTruthy();
  });

  it('closes the detail panel on Escape', () => {
    setupMocks();
    render(<CampaignPaymentsTab campaign={campaign} payments={setupPayments({ payouts: [samplePayout] })} />);

    fireEvent.click(journalRows()[0]);
    expect(document.querySelector('.side-panel')).toBeTruthy();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(document.querySelector('.side-panel')).toBeNull();
  });
});
