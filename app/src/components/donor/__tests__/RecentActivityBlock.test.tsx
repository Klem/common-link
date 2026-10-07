import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { RecentActivityBlock } from '../RecentActivityBlock';
import { getDonorFeed, markDonorFeedSeen } from '@/lib/api/donor';
import { DonorFeedItemType, type DonorFeedItemDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

vi.mock('@/lib/api/donor', () => ({
  getDonorFeed: vi.fn(),
  markDonorFeedSeen: vi.fn(),
}));

const feed: DonorFeedItemDto[] = [
  {
    type: DonorFeedItemType.MILESTONE_REACHED,
    campaignId: 'camp-1',
    campaignName: 'Reforestation',
    associationName: 'Terre Verte',
    occurredAt: '2026-09-01T00:00:00Z',
    label: 'Reforestation a atteint le palier « Palier 1 ».',
  },
];

describe('RecentActivityBlock', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the feed items with their server-resolved label', async () => {
    vi.mocked(getDonorFeed).mockResolvedValue(feed);
    render(<RecentActivityBlock />);

    await waitFor(() =>
      expect(screen.getByText('Reforestation a atteint le palier « Palier 1 ».')).toBeInTheDocument(),
    );
  });

  it('marks the feed as seen only after it has been fetched', async () => {
    vi.mocked(getDonorFeed).mockResolvedValue(feed);
    render(<RecentActivityBlock />);

    await waitFor(() => expect(getDonorFeed).toHaveBeenCalled());
    await waitFor(() => expect(markDonorFeedSeen).toHaveBeenCalledTimes(1));
  });

  it('shows an empty message in both columns when there is no new event', async () => {
    vi.mocked(getDonorFeed).mockResolvedValue([]);
    render(<RecentActivityBlock />);

    await waitFor(() => expect(screen.getAllByText('empty')).toHaveLength(2));
  });

  it('splits proof items (payout/milestone) from action items (campaign completed) into two columns', async () => {
    const mixedFeed: DonorFeedItemDto[] = [
      ...feed,
      {
        type: DonorFeedItemType.CAMPAIGN_COMPLETED,
        campaignId: 'camp-2',
        campaignName: 'Cantine solidaire',
        associationName: 'Solidarité Repas',
        occurredAt: '2026-09-02T00:00:00Z',
        label: 'Cantine solidaire est clôturée.',
      },
    ];
    vi.mocked(getDonorFeed).mockResolvedValue(mixedFeed);
    render(<RecentActivityBlock />);

    await waitFor(() => expect(screen.getByText('Reforestation a atteint le palier « Palier 1 ».')).toBeInTheDocument());
    expect(screen.getByText('Cantine solidaire est clôturée.')).toBeInTheDocument();
    expect(screen.queryByText('empty')).not.toBeInTheDocument();
  });
});
