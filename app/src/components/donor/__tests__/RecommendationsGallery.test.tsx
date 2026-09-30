import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { RecommendationsGallery } from '../RecommendationsGallery';
import { getDonorRecommendations } from '@/lib/api/donor';
import type { DonorRecommendationDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/donor', () => ({
  getDonorRecommendations: vi.fn(),
}));

const recommendations: DonorRecommendationDto[] = [
  {
    campaignId: 'camp-1',
    campaignName: 'Reforestation',
    campaignEmoji: '🌳',
    associationName: 'Terre Verte',
    category: 'Environnement',
    coverImage: null,
    goal: 1000,
    raised: 250,
    donationUrl: 'https://commonlink.org/fr/lp/clk_1',
    matchedCategory: 'Environnement',
  },
  {
    campaignId: 'camp-2',
    campaignName: 'Cantine scolaire',
    campaignEmoji: '🍎',
    associationName: 'École Kaolack',
    category: 'Éducation',
    coverImage: null,
    goal: 2000,
    raised: 500,
    donationUrl: 'https://commonlink.org/fr/lp/clk_2',
    matchedCategory: null,
  },
];

describe('RecommendationsGallery', () => {
  it('renders one card per recommendation', async () => {
    vi.mocked(getDonorRecommendations).mockResolvedValue(recommendations);
    render(<RecommendationsGallery />);

    await waitFor(() => expect(screen.getByText('Reforestation')).toBeInTheDocument());
    expect(screen.getByText('Cantine scolaire')).toBeInTheDocument();
  });

  it('filtering by cause reduces the list to matching campaigns', async () => {
    vi.mocked(getDonorRecommendations).mockResolvedValue(recommendations);
    render(<RecommendationsGallery />);

    await waitFor(() => expect(screen.getByText('Reforestation')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Éducation' }));

    expect(screen.queryByText('Reforestation')).not.toBeInTheDocument();
    expect(screen.getByText('Cantine scolaire')).toBeInTheDocument();
  });
});
