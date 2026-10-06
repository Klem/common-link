import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DonorAssociationCard } from '../DonorAssociationCard';
import { DonorCampaignStatus, type DonorAssociationDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useLocale: () => 'fr',
  useTranslations: () => (key: string) => key,
}));

const association: DonorAssociationDto = {
  associationId: 'asso-1',
  name: 'Terre Verte',
  category: 'ENVIRONNEMENT',
  totalDonated: 250,
  publishedPayoutCount: 4,
  campaignCount: 2,
  lastDonationAt: '2026-03-15T10:00:00Z',
  donationUrl: 'https://commonlink.org/fr/lp/clk_terreverte',
  campaignStatus: DonorCampaignStatus.LIVE,
  campaignId: 'camp-1',
  campaignName: 'Rénovation école',
};

describe('DonorAssociationCard', () => {
  it('renders the association name and category', () => {
    render(<DonorAssociationCard association={association} colorIndex={0} />);
    expect(screen.getByText('Terre Verte')).toBeInTheDocument();
    expect(screen.getByText('🌍 ENVIRONNEMENT')).toBeInTheDocument();
  });

  it('renders the aggregate figures (donated total and published payouts only)', () => {
    render(<DonorAssociationCard association={association} colorIndex={0} />);
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText(/250/)).toBeInTheDocument();
  });

  it('renders the logo image by default', () => {
    const { container } = render(<DonorAssociationCard association={association} colorIndex={0} />);
    expect(container.querySelector('img')).toBeInTheDocument();
  });

  it('falls back to initials when the logo fails to load', () => {
    const { container } = render(<DonorAssociationCard association={association} colorIndex={0} />);
    fireEvent.error(container.querySelector('img')!);
    expect(container.querySelector('img')).not.toBeInTheDocument();
    expect(screen.getByText('TV')).toBeInTheDocument();
  });

  it('shows the campaign CTA link when campaignStatus is LIVE', () => {
    render(<DonorAssociationCard association={association} colorIndex={0} />);
    const link = screen.getByRole('link', { name: 'campaignStatus.ctaLive' });
    expect(link).toHaveAttribute('href', '/fr/dashboard/donor/campaigns/camp-1');
  });

  it('renders the campaign description sentence under the freshness-tag when a campaignName is set', () => {
    render(<DonorAssociationCard association={association} colorIndex={0} />);
    expect(screen.getByText('campaignStatus.liveDescription')).toBeInTheDocument();
  });

  it('renders no freshness-tag, description or campaign CTA when campaignStatus is NONE', () => {
    render(
      <DonorAssociationCard
        association={{
          ...association,
          campaignStatus: DonorCampaignStatus.NONE,
          campaignId: null,
          campaignName: null,
        }}
        colorIndex={0}
      />,
    );
    expect(screen.queryByRole('link', { name: /campaignStatus/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/campaignStatus\.(live|completed)Description/)).not.toBeInTheDocument();
  });

  it('rotates the avatar color variant by colorIndex, not identity', () => {
    const { container, rerender } = render(
      <DonorAssociationCard association={{ ...association, donationUrl: null }} colorIndex={0} />,
    );
    fireEvent.error(container.querySelector('img')!);
    expect(container.querySelector('.asso-avatar.a1')).toBeInTheDocument();

    rerender(<DonorAssociationCard association={{ ...association, donationUrl: null }} colorIndex={1} />);
    expect(container.querySelector('.asso-avatar.a2')).toBeInTheDocument();
  });
});
