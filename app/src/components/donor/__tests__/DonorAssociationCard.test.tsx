import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DonorAssociationCard } from '../DonorAssociationCard';
import type { DonorAssociationDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const association: DonorAssociationDto = {
  associationId: 'asso-1',
  name: 'Terre Verte',
  category: 'Environnement',
  totalDonated: 250,
  publishedPayoutCount: 4,
  campaignCount: 2,
  lastDonationAt: '2026-03-15T10:00:00Z',
  donationUrl: 'https://commonlink.org/fr/lp/clk_terreverte',
};

describe('DonorAssociationCard', () => {
  it('renders the association name and category', () => {
    render(<DonorAssociationCard association={association} />);
    expect(screen.getByText('Terre Verte')).toBeInTheDocument();
    expect(screen.getByText('Environnement')).toBeInTheDocument();
  });

  it('renders the aggregate figures', () => {
    render(<DonorAssociationCard association={association} />);
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('renders the logo image by default', () => {
    const { container } = render(<DonorAssociationCard association={association} />);
    expect(container.querySelector('img')).toBeInTheDocument();
  });

  it('falls back to initials when the logo fails to load', () => {
    const { container } = render(<DonorAssociationCard association={association} />);
    fireEvent.error(container.querySelector('img')!);
    expect(container.querySelector('img')).not.toBeInTheDocument();
    expect(screen.getByText('TV')).toBeInTheDocument();
  });

  it('renders "—" when there is no last donation date', () => {
    render(<DonorAssociationCard association={{ ...association, lastDonationAt: null }} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});
