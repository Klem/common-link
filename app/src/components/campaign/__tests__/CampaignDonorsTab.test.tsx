import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
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
  milestones: [],
  budgetSections: [],
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
  providerRef: 'monerium:abc-123',
  confirmedAt: '2026-03-15T10:00:00Z',
  createdAt: '2026-03-15T09:00:00Z',
  onChain: true,
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
  isLoading: false,
  error: null,
  selectedDonor: null,
  donorDonations: [],
  isDonorLoading: false,
  selectedDonation: null,
  setPage: vi.fn(),
  setSearch: vi.fn(),
  setSort: vi.fn(),
  selectDonor: vi.fn(),
  closeDonor: vi.fn(),
  selectDonation: vi.fn(),
  closeDonation: vi.fn(),
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

  it('shows "Anonyme" as-is for anonymous donors', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getAllByText('Anonyme').length).toBeGreaterThan(0);
  });

  it('renders search input and sort select', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getByPlaceholderText('search.placeholder')).toBeInTheDocument();
    expect(screen.getByDisplayValue('sort.amount')).toBeInTheDocument();
  });

  it('calls setSearch when typing in search input', () => {
    render(<CampaignDonorsTab campaign={campaign} />);
    fireEvent.change(screen.getByPlaceholderText('search.placeholder'), {
      target: { value: 'Marie' },
    });
    expect(defaultHook.setSearch).toHaveBeenCalledWith('Marie');
  });

  it('opens donor detail when clicking Voir', async () => {
    const selectDonor = vi.fn();
    mockUseDonors.mockReturnValue({ ...defaultHook, selectDonor });
    render(<CampaignDonorsTab campaign={campaign} />);
    const viewButtons = screen.getAllByText('table.view');
    fireEvent.click(viewButtons[0]);
    expect(selectDonor).toHaveBeenCalledWith(donor1);
  });

  it('renders donor detail panel when selectedDonor is set', () => {
    mockUseDonors.mockReturnValue({
      ...defaultHook,
      selectedDonor: donor1,
      donorDonations: [donation1],
      isDonorLoading: false,
    });
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.getAllByText('Marie L.').length).toBeGreaterThan(0);
    expect(screen.getByText('detail.transactions')).toBeInTheDocument();
  });

  it('shows donation list and hides noTx in donor detail panel', () => {
    mockUseDonors.mockReturnValue({
      ...defaultHook,
      selectedDonor: donor1,
      donorDonations: [donation1],
      isDonorLoading: false,
    });
    render(<CampaignDonorsTab campaign={campaign} />);
    expect(screen.queryByText('detail.noTx')).not.toBeInTheDocument();
  });

  it('calls closeDonor when closing detail panel', () => {
    const closeDonor = vi.fn();
    mockUseDonors.mockReturnValue({
      ...defaultHook,
      selectedDonor: donor1,
      closeDonor,
    });
    render(<CampaignDonorsTab campaign={campaign} />);
    const closeBtn = screen.getByText('detail.close');
    fireEvent.click(closeBtn);
    expect(closeDonor).toHaveBeenCalled();
  });

  it('shows donor detail loading spinner', () => {
    mockUseDonors.mockReturnValue({
      ...defaultHook,
      selectedDonor: donor1,
      donorDonations: [],
      isDonorLoading: true,
    });
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
    expect(screen.getByText('1 / 3')).toBeInTheDocument();
  });
});
