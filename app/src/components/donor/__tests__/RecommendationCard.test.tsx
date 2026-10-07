import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { RecommendationCard } from '../RecommendationCard';
import type { DonorRecommendationDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, values?: Record<string, unknown>) => {
    if (key === 'reason') return `Parce que vous soutenez déjà la cause ${values?.category}`;
    if (key === 'donate') return `Soutenir ${values?.name}`;
    return key;
  },
}));

const base: DonorRecommendationDto = {
  campaignId: 'camp-1',
  campaignName: 'Reforestation',
  campaignEmoji: '🌳',
  associationName: 'Terre Verte',
  category: 'ENVIRONNEMENT',
  coverImage: null,
  goal: 1000,
  raised: 250,
  donationUrl: 'https://commonlink.org/fr/lp/clk_x',
  matchedCategory: null,
};

describe('RecommendationCard', () => {
  it('renders the campaign and association name', () => {
    render(<RecommendationCard recommendation={base} />);
    expect(screen.getByText('Reforestation')).toBeInTheDocument();
    expect(screen.getByText('Terre Verte')).toBeInTheDocument();
  });

  it('shows the justification only when matchedCategory is set', () => {
    const { rerender } = render(<RecommendationCard recommendation={base} />);
    expect(screen.queryByText(/Parce que vous soutenez déjà/)).not.toBeInTheDocument();

    rerender(<RecommendationCard recommendation={{ ...base, matchedCategory: 'ENVIRONNEMENT' }} />);
    expect(screen.getByText('Parce que vous soutenez déjà la cause 🌍 ENVIRONNEMENT')).toBeInTheDocument();
  });

  it('always offers a donation link, since findPublicLive already guarantees eligibility', () => {
    render(<RecommendationCard recommendation={base} />);
    expect(screen.getByRole('link', { name: /Terre Verte/ })).toHaveAttribute(
      'href',
      'https://commonlink.org/fr/lp/clk_x',
    );
  });
});
