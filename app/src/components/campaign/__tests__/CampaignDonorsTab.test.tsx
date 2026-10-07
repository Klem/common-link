import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { CampaignDonorsTab } from '../CampaignDonorsTab';
import type { CampaignDto } from '@/types/campaign';

// ── Mocks ──────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, unknown>) => {
    if (params) return `${key}:${JSON.stringify(params)}`;
    return key;
  },
}));

vi.mock('@/hooks/campaign/useCampaignDonors');

import { useCampaignDonors } from '@/hooks/campaign/useCampaignDonors';
const mockUseDonors = useCampaignDonors as ReturnType<typeof vi.fn>;

// ── Fixtures ───────────────────────────────────────────────────────────────────

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
  category: null,
  actionPlace: null,
  reason: null,
  impactGoals: null,
  coverImage: null,
  milestones: [],
  budgetSections: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const donor1 = {
  donorId: 'donor-1',
  displayName: 'Marie L.',
  totalAmount: 150,
  txCount: 3,
  lastDonationAt: '2026-03-15T10:00:00Z',
};

const anonymousDonor = {
  donorId: 'donor-anon',
  displayName: 'Anonyme',
  totalAmount: 50,
  txCount: 1,
  lastDonationAt: '2026-03-10T08:00:00Z',
};

const donation1 = {
  id: 'don-1',
  amount: 100,
  providerRef: 'mollie:tr_abc123',
  confirmedAt: '2026-03-15T10:00:00Z',
  createdAt: '2026-03-15T09:00:00Z',
  onChain: true,
};

const donation2 = {
  id: 'don-2',
  amount: 25,
  providerRef: 'mollie:tr_def456',
  confirmedAt: null,
  createdAt: '2026-03-16T09:00:00Z',
  onChain: false,
};

const basePage = {
  content: [donor1, anonymousDonor],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 12,
};

const defaultHook = {
  donorsPage: basePage,
  page: 0,
  search: '',
  sort: 'amount',
  direction: 'desc',
  isLoading: false,
  error: null,
  openDonorId: null,
  donorDonations: [],
  isDonorLoading: false,
  setPage: vi.fn(),
  setSearch: vi.fn(),
  toggleSort: vi.fn(),
  toggleDonor: vi.fn(),
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('CampaignDonorsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseDonors.mockReturnValue(defaultHook);
  });

  it('renders loading state', () => {
    mockUseDonors.mockReturnValue({ ...defaultHook, isLoading: true, donorsPage: null });
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(document.querySelector('.animate-spin')).toBeTruthy();
  });

  it('renders error state', () => {
    mockUseDonors.mockReturnValue({ ...defaultHook, isLoading: false, error: 'error', donorsPage: null });
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getByText('error')).toBeInTheDocument();
  });

  it('renders empty state when no donors', () => {
    mockUseDonors.mockReturnValue({
      ...defaultHook,
      donorsPage: { ...basePage, content: [], totalElements: 0 },
    });
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getByText('empty')).toBeInTheDocument();
  });

  it('renders donor list with names and amounts', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getAllByText('Marie L.').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Anonyme').length).toBeGreaterThan(0);
  });

  it('renders search input and no sort select (headers sort instead)', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getByPlaceholderText('search.placeholder')).toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('calls setSearch when typing in search input', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    fireEvent.change(screen.getByPlaceholderText('search.placeholder'), {
      target: { value: 'Marie' },
    });
    expect(defaultHook.setSearch).toHaveBeenCalledWith('Marie');
  });

  it('no longer renders a "view" button', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.queryByText('table.view')).not.toBeInTheDocument();
  });

  it.each([
    ['table.donor', 'name'],
    ['table.amount', 'amount'],
    ['table.transactions', 'count'],
    ['table.lastDonation', 'date'],
  ])('clicking header %s sorts server-side by %s', (label, key) => {
    render(<CampaignDonorsTab campaign={campaign} />);
    fireEvent.click(screen.getByRole('button', { name: new RegExp(`"column":"${label}"`) }));
    expect(defaultHook.toggleSort).toHaveBeenCalledWith(key);
  });

  it('marks the active sort header and its direction', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    const amountHeader = screen.getByRole('button', { name: /"column":"table.amount"/ });
    expect(amountHeader).toHaveClass('active');
    expect(amountHeader.querySelector('.th-sort-chev')).toHaveClass('desc');
  });

  it('chevron toggles the donor row', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    const chevron = screen.getByRole('button', { name: 'table.showDetail — Marie L.' });
    expect(chevron).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(chevron);
    expect(defaultHook.toggleDonor).toHaveBeenCalledWith('donor-1');
  });

  it('renders the transactions sub-table under the open donor only', () => {
    mockUseDonors.mockReturnValue({ ...defaultHook, openDonorId: 'donor-1', donorDonations: [donation1, donation2] });
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getByRole('button', { name: 'table.hideDetail — Marie L.' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'table.showDetail — Anonyme' })).toHaveAttribute('aria-expanded', 'false');
    const detailRow = document.getElementById('donor-detail-donor-1')!;
    expect(detailRow).toBeInTheDocument();
    expect(document.getElementById('donor-detail-donor-anon')).not.toBeInTheDocument();
    expect(within(detailRow).getByText('tx.ref')).toBeInTheDocument();
    expect(within(detailRow).getAllByRole('row')).toHaveLength(3); // header + 2 donations
    expect(screen.queryByText('detail.noTx')).not.toBeInTheDocument();
  });

  it('sorts the transactions sub-table locally by amount', () => {
    mockUseDonors.mockReturnValue({ ...defaultHook, openDonorId: 'donor-1', donorDonations: [donation1, donation2] });
    render(<CampaignDonorsTab campaign={campaign} />);
    const detailRow = document.getElementById('donor-detail-donor-1')!;
    const amountHeader = within(detailRow).getByRole('button', { name: /"column":"tx.amount"/ });

    fireEvent.click(amountHeader);
    let rows = within(detailRow).getAllByRole('row').slice(1);
    expect(rows[0]).toHaveTextContent('25,00');
    expect(rows[1]).toHaveTextContent('100,00');

    fireEvent.click(amountHeader);
    rows = within(detailRow).getAllByRole('row').slice(1);
    expect(rows[0]).toHaveTextContent('100,00');
  });

  it('shows noTx when the open donor has no donations', () => {
    mockUseDonors.mockReturnValue({ ...defaultHook, openDonorId: 'donor-1', donorDonations: [] });
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getByText('detail.noTx')).toBeInTheDocument();
  });

  it('shows donor detail loading spinner', () => {
    mockUseDonors.mockReturnValue({ ...defaultHook, openDonorId: 'donor-1', isDonorLoading: true });
    render(<CampaignDonorsTab campaign={campaign} />);
    const spinners = document.querySelectorAll('.animate-spin');
    expect(spinners.length).toBeGreaterThan(0);
  });

  it('renders pager when totalPages > 1', () => {
    mockUseDonors.mockReturnValue({
      ...defaultHook,
      donorsPage: { ...basePage, totalPages: 3, totalElements: 36 },
    });
    render(<CampaignDonorsTab campaign={campaign} />);
    // pager renders numbered buttons: 1, 2, 3
    expect(document.querySelector('.pager')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '3' })).toBeInTheDocument();
  });
});
