import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CampaignReportingTab } from '../CampaignReportingTab';
import type { CampaignDto } from '@/types/campaign';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/hooks/campaign/useReporting');

import { useReporting } from '@/hooks/campaign/useReporting';
const mockUseReporting = useReporting as ReturnType<typeof vi.fn>;

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

const mockData = {
  charges: [
    { sectionCode: '60', sectionName: 'Matières premières', planned: 5000, actual: 6000, variance: 1000 },
  ],
  produits: [
    { sectionCode: '74', sectionName: 'Subventions', planned: 8000, actual: 7500, variance: -500 },
  ],
  totals: {
    totalPlannedCharges: 5000,
    totalActualCharges: 6000,
    totalPlannedProduits: 8000,
    totalActualProduits: 7500,
  },
};

describe('CampaignReportingTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading spinner while fetching', () => {
    mockUseReporting.mockReturnValue({ data: null, isLoading: true, error: null, refetch: vi.fn() });
    const { container } = render(<CampaignReportingTab campaign={campaign} />);
    expect(container.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('shows error alert on failure', () => {
    mockUseReporting.mockReturnValue({ data: null, isLoading: false, error: 'reporting.tab.error', refetch: vi.fn() });
    render(<CampaignReportingTab campaign={campaign} />);
    expect(screen.getByText('reporting.tab.loadError')).toBeInTheDocument();
  });

  it('renders section names from data', () => {
    mockUseReporting.mockReturnValue({ data: mockData, isLoading: false, error: null, refetch: vi.fn() });
    render(<CampaignReportingTab campaign={campaign} />);
    // Each name appears in both VarianceTable and Donut legend
    expect(screen.getAllByText('Matières premières').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Subventions').length).toBeGreaterThanOrEqual(1);
  });

  it('renders stat card labels', () => {
    mockUseReporting.mockReturnValue({ data: mockData, isLoading: false, error: null, refetch: vi.fn() });
    render(<CampaignReportingTab campaign={campaign} />);
    expect(screen.getByText('reporting.tab.statsChargesPlanned')).toBeInTheDocument();
    expect(screen.getByText('reporting.tab.statsProduitsActual')).toBeInTheDocument();
  });

  it('renders section titles', () => {
    mockUseReporting.mockReturnValue({ data: mockData, isLoading: false, error: null, refetch: vi.fn() });
    render(<CampaignReportingTab campaign={campaign} />);
    expect(screen.getByText('reporting.tab.chargesTitle')).toBeInTheDocument();
    expect(screen.getByText('reporting.tab.produitsTitle')).toBeInTheDocument();
  });
});
