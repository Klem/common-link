import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { DonationTraceabilityModal } from '../DonationTraceabilityModal';
import { getDonationJourney } from '@/lib/api/donor';
import type { DonorDonationJourneyDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/donor', () => ({
  getDonationJourney: vi.fn(),
}));

const journey: DonorDonationJourneyDto = {
  donationId: 'don-1',
  steps: [
    { step: 'RECEIVED', reached: true, reachedAt: '2026-01-01T00:00:00Z' },
    { step: 'RECORDED', reached: true, reachedAt: '2026-01-02T00:00:00Z' },
    { step: 'SPENT', reached: true, reachedAt: '2026-01-05T00:00:00Z' },
    { step: 'IMPACT_REPORTED', reached: false, reachedAt: null },
  ],
  previousDonationId: null,
  nextDonationId: null,
  usedAmount: 80,
  remainingAmount: 20,
  fundedPayouts: [
    { payoutId: 'payout-1', label: 'Achat de graines', amountImputed: 80, confirmedAt: '2026-01-05T00:00:00Z' },
  ],
};

describe('DonationTraceabilityModal', () => {
  it('renders nothing when donationId is null', () => {
    const { container } = render(<DonationTraceabilityModal donationId={null} onClose={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the used amount and the funded expenses', async () => {
    vi.mocked(getDonationJourney).mockResolvedValue(journey);
    render(<DonationTraceabilityModal donationId="don-1" onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText(/Achat de graines/)).toBeInTheDocument());
    expect(screen.getAllByText(/80,00\s?€/).length).toBeGreaterThan(0);
  });

  it('never renders a public/shareable link block', async () => {
    vi.mocked(getDonationJourney).mockResolvedValue(journey);
    render(<DonationTraceabilityModal donationId="don-1" onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText(/Achat de graines/)).toBeInTheDocument());
    expect(screen.queryByText(/preuve/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', async () => {
    vi.mocked(getDonationJourney).mockResolvedValue(journey);
    const onClose = vi.fn();
    render(<DonationTraceabilityModal donationId="don-1" onClose={onClose} />);

    await waitFor(() => expect(screen.getByText(/Achat de graines/)).toBeInTheDocument());
    screen.getByLabelText('close').click();
    expect(onClose).toHaveBeenCalled();
  });
});
