import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { RecentActivityBlock } from '../RecentActivityBlock';
import { getDonorFeed, markDonorFeedSeen } from '@/lib/api/donor';
import { DonorFeedItemType, type DonorFeedItemDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
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

  it('shows an empty message when there is no new event', async () => {
    vi.mocked(getDonorFeed).mockResolvedValue([]);
    render(<RecentActivityBlock />);

    await waitFor(() => expect(screen.getByText('empty')).toBeInTheDocument());
  });
});
