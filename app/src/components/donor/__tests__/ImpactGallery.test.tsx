import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { ImpactGallery } from '../ImpactGallery';
import { getDonorImpacts, getDonorStats } from '@/lib/api/donor';
import type { DonorImpactDto, DonorStatsDto } from '@/types/donor';

// Raw keys everywhere except the two fragments the collective wording (D6) composes around
// association-authored free text — those need real strings to prove the sentence assembles
// correctly (no run-on text, no doubled punctuation).
const WORDING_FRAGMENTS: Record<string, string> = {
  wordingPrefix: 'Ce projet a',
  wordingSuffix: 'Vous y avez contribué.',
};
vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => WORDING_FRAGMENTS[key] ?? key,
}));

vi.mock('@/lib/api/donor', () => ({
  getDonorImpacts: vi.fn(),
  getDonorStats: vi.fn(),
}));

const stats: DonorStatsDto = { totalDonated: 500, donationCount: 5, associationCount: 2, estimatedTaxReduction: 300 };

const impacts: DonorImpactDto[] = [
  {
    campaignId: 'camp-1',
    campaignName: 'Reforestation',
    campaignEmoji: '🌳',
    associationName: 'Terre Verte',
    category: 'Environnement',
    impactGoals: "50 arbres plantés",
    storyText: null,
  },
  {
    campaignId: 'camp-2',
    campaignName: 'Cantine scolaire',
    campaignEmoji: '🍎',
    associationName: 'École Kaolack',
    category: 'Éducation',
    impactGoals: null,
    storyText: '200 repas servis cette année.',
  },
];

describe('ImpactGallery', () => {
  it('renders one card per campaign', async () => {
    vi.mocked(getDonorImpacts).mockResolvedValue(impacts);
    vi.mocked(getDonorStats).mockResolvedValue(stats);
    render(<ImpactGallery />);

    await waitFor(() => expect(screen.getByText(/Reforestation/)).toBeInTheDocument());
    expect(screen.getByText(/Cantine scolaire/)).toBeInTheDocument();
  });

  it('filtering by cause reduces the list to matching campaigns', async () => {
    vi.mocked(getDonorImpacts).mockResolvedValue(impacts);
    vi.mocked(getDonorStats).mockResolvedValue(stats);
    render(<ImpactGallery />);

    await waitFor(() => expect(screen.getByText(/Reforestation/)).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Éducation' }));

    expect(screen.queryByText(/Reforestation/)).not.toBeInTheDocument();
    expect(screen.getByText(/Cantine scolaire/)).toBeInTheDocument();
  });

  it('never renders a donor amount or share — collective wording only', async () => {
    vi.mocked(getDonorImpacts).mockResolvedValue(impacts);
    vi.mocked(getDonorStats).mockResolvedValue(stats);
    const { container } = render(<ImpactGallery />);

    await waitFor(() => expect(screen.getByText(/Reforestation/)).toBeInTheDocument());

    // Only the cumulative "total donated" stat card may show a euro amount — no card shows a
    // percentage, and no card claims a donor-specific share.
    expect(container.textContent).not.toMatch(/%/);
    expect(screen.queryByText(/financé/i)).not.toBeInTheDocument();
  });

  it('composes the collective wording as one well-formed sentence, from a bare noun phrase', async () => {
    vi.mocked(getDonorImpacts).mockResolvedValue(impacts);
    vi.mocked(getDonorStats).mockResolvedValue(stats);
    render(<ImpactGallery />);

    // impactGoals ("50 arbres plantés") has no terminal punctuation — the composed sentence
    // must still read as two proper sentences, never running the clauses together.
    await waitFor(() =>
      expect(
        screen.getByText('Ce projet a 50 arbres plantés. Vous y avez contribué.'),
      ).toBeInTheDocument(),
    );
  });

  it('composes the collective wording without doubling an existing terminal period', async () => {
    vi.mocked(getDonorImpacts).mockResolvedValue(impacts);
    vi.mocked(getDonorStats).mockResolvedValue(stats);
    render(<ImpactGallery />);

    // storyText ("200 repas servis cette année.") already ends with a period.
    await waitFor(() =>
      expect(
        screen.getByText('Ce projet a 200 repas servis cette année. Vous y avez contribué.'),
      ).toBeInTheDocument(),
    );
    expect(screen.queryByText(/\.\./)).not.toBeInTheDocument();
  });

  it('"Voir plus" reveals additional cards beyond the first page', async () => {
    const many: DonorImpactDto[] = Array.from({ length: 12 }, (_, i) => ({
      campaignId: `camp-${i}`,
      campaignName: `Campagne ${i}`,
      campaignEmoji: '🌍',
      associationName: 'Asso',
      category: null,
      impactGoals: 'Impact',
      storyText: null,
    }));
    vi.mocked(getDonorImpacts).mockResolvedValue(many);
    vi.mocked(getDonorStats).mockResolvedValue(stats);
    render(<ImpactGallery />);

    await waitFor(() => expect(screen.getByText(/Campagne 0$/)).toBeInTheDocument());
    expect(screen.queryByText(/Campagne 10$/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByText('showMore'));

    expect(screen.getByText(/Campagne 10$/)).toBeInTheDocument();
  });
});
