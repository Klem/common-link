import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DonationJourneyTimeline } from '../DonationJourneyTimeline';
import { getDonationJourney } from '@/lib/api/donor';
import type { DonorDonationJourneyDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/donor', () => ({
  getDonationJourney: vi.fn(),
}));

const baseJourney: DonorDonationJourneyDto = {
  donationId: 'don-1',
  steps: [
    { step: 'RECEIVED', reached: true, reachedAt: '2026-01-01T00:00:00Z' },
    { step: 'RECORDED', reached: true, reachedAt: '2026-01-02T00:00:00Z' },
    { step: 'SPENT', reached: false, reachedAt: null },
    { step: 'IMPACT_REPORTED', reached: false, reachedAt: null },
  ],
  previousDonationId: null,
  nextDonationId: 'don-2',
  usedAmount: 0,
  remainingAmount: 100,
  fundedPayouts: [],
};

describe('DonationJourneyTimeline', () => {
  it('renders an empty state when there is no donation yet', () => {
    render(<DonationJourneyTimeline initialDonationId={null} />);
    expect(screen.getByText('empty.title')).toBeInTheDocument();
  });

  it('renders all 4 steps with the current step marked', async () => {
    vi.mocked(getDonationJourney).mockResolvedValue(baseJourney);
    render(<DonationJourneyTimeline initialDonationId="don-1" />);

    await waitFor(() => expect(screen.getByText('steps.RECEIVED.label')).toBeInTheDocument());
    expect(screen.getByText('steps.RECORDED.label')).toBeInTheDocument();
    expect(screen.getByText('steps.SPENT.label')).toBeInTheDocument();
    expect(screen.getByText('steps.IMPACT_REPORTED.label')).toBeInTheDocument();

    const currentStepItem = screen.getByText('steps.SPENT.label').closest('li');
    expect(currentStepItem).toHaveAttribute('aria-current', 'step');
  });

  it('disables previous/next navigation at the ends of the history', async () => {
    vi.mocked(getDonationJourney).mockResolvedValue(baseJourney);
    render(<DonationJourneyTimeline initialDonationId="don-1" />);

    await waitFor(() => expect(screen.getByText('previous')).toBeInTheDocument());
    expect(screen.getByText('previous')).toBeDisabled();
    expect(screen.getByText('next')).not.toBeDisabled();
  });

  it('navigates to the next donation when clicked', async () => {
    vi.mocked(getDonationJourney).mockResolvedValueOnce(baseJourney).mockResolvedValueOnce({
      ...baseJourney,
      donationId: 'don-2',
      previousDonationId: 'don-1',
      nextDonationId: null,
    });
    render(<DonationJourneyTimeline initialDonationId="don-1" />);

    await waitFor(() => expect(screen.getByText('next')).not.toBeDisabled());
    fireEvent.click(screen.getByText('next'));

    await waitFor(() => expect(getDonationJourney).toHaveBeenCalledWith('don-2'));
  });
});
