import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import DonorCampaignReportPage from '../page';
import { getCampaignReport, downloadCampaignReportPdf } from '@/lib/api/donor';
import type { DonorCampaignReportDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('next/navigation', () => ({
  useParams: () => ({ campaignId: 'camp-1' }),
}));

vi.mock('@/lib/api/donor', () => ({
  getCampaignReport: vi.fn(),
  downloadCampaignReportPdf: vi.fn(),
}));

const report: DonorCampaignReportDto = {
  campaignId: 'camp-1',
  campaignName: 'Reforestation',
  campaignEmoji: '🌳',
  associationName: 'Terre Verte',
  status: 'LIVE',
  goal: 10000,
  raised: 4000,
  donorContribution: 150,
  milestones: [
    {
      id: 'm-1',
      emoji: '🌱',
      title: 'Premier hectare planté',
      description: null,
      transparencyCommitment: null,
      targetAmount: 2000,
      status: 'REACHED',
      sortOrder: 0,
      reachedAt: '2026-02-01T00:00:00Z',
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  confirmedPayouts: [
    {
      payoutId: 'payout-1',
      label: 'Achat de graines',
      amount: 500,
      payeeName: 'Pépinière du Nord',
      confirmedAt: '2026-02-05T00:00:00Z',
      sectionCode: '60',
    },
  ],
  variance: {
    charges: [
      { sectionCode: '60', sectionName: 'Achats', planned: 1000, actual: 500, variance: -500 },
    ],
    produits: [],
    totals: {
      totalPlannedCharges: 1000,
      totalActualCharges: 500,
      totalPlannedProduits: 4000,
      totalActualProduits: 4000,
    },
  },
  registryBannerText:
    'Les dons sont inscrits dans un registre public. Les dépenses sont tracées et vérifiées ; leur inscription au registre public est en cours de déploiement.',
};

describe('DonorCampaignReportPage', () => {
  it('renders the hero, contribution and registry banner', async () => {
    vi.mocked(getCampaignReport).mockResolvedValue(report);
    render(<DonorCampaignReportPage />);

    await waitFor(() => expect(screen.getByText(/Reforestation/)).toBeInTheDocument());
    expect(screen.getByText('Terre Verte')).toBeInTheDocument();
    expect(screen.getByText(report.registryBannerText)).toBeInTheDocument();
  });

  it('reveals the confirmed-payout detail only after expanding its own category', async () => {
    vi.mocked(getCampaignReport).mockResolvedValue(report);
    render(<DonorCampaignReportPage />);

    await waitFor(() => expect(screen.getByText(/funds.showDetail/)).toBeInTheDocument());
    expect(screen.queryByText('Achat de graines')).not.toBeInTheDocument();

    const toggle = screen.getByText(/funds.showDetail/);
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(toggle);

    expect(screen.getByText(/Achat de graines/)).toBeInTheDocument();
    expect(screen.getByText(/funds.hideDetail/)).toHaveAttribute('aria-expanded', 'true');
  });

  it('only shows a category\'s own confirmed payouts, not other categories\'', async () => {
    const multiCategoryReport: DonorCampaignReportDto = {
      ...report,
      confirmedPayouts: [
        ...report.confirmedPayouts,
        {
          payoutId: 'payout-2',
          label: 'Salaire coordinateur',
          amount: 800,
          payeeName: 'J. Martin',
          confirmedAt: '2026-02-10T00:00:00Z',
          sectionCode: '64',
        },
      ],
      variance: {
        ...report.variance,
        charges: [
          ...report.variance.charges,
          { sectionCode: '64', sectionName: 'Salaires', planned: 2000, actual: 800, variance: -1200 },
        ],
      },
    };
    vi.mocked(getCampaignReport).mockResolvedValue(multiCategoryReport);
    render(<DonorCampaignReportPage />);

    await waitFor(() => expect(screen.getAllByText(/funds.showDetail/)).toHaveLength(2));
    fireEvent.click(screen.getAllByText(/funds.showDetail/)[0]);

    expect(screen.getByText(/Achat de graines/)).toBeInTheDocument();
    expect(screen.queryByText(/Salaire coordinateur/)).not.toBeInTheDocument();
  });

  it('never renders the word "engagé"', async () => {
    vi.mocked(getCampaignReport).mockResolvedValue(report);
    const { container } = render(<DonorCampaignReportPage />);
    await waitFor(() => expect(screen.getByText(/Reforestation/)).toBeInTheDocument());
    expect(container.textContent?.toLowerCase()).not.toContain('engagé');
  });

  it('triggers the PDF download when the export button is clicked', async () => {
    vi.mocked(getCampaignReport).mockResolvedValue(report);
    vi.mocked(downloadCampaignReportPdf).mockResolvedValue(new Blob(['pdf']));
    render(<DonorCampaignReportPage />);

    await waitFor(() => expect(screen.getByText('downloadPdf')).toBeInTheDocument());
    fireEvent.click(screen.getByText('downloadPdf'));

    await waitFor(() => expect(downloadCampaignReportPdf).toHaveBeenCalledWith('camp-1'));
  });
});
