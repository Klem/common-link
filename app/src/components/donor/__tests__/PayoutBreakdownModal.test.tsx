import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PayoutBreakdownModal } from '../PayoutBreakdownModal';
import { getPayoutBreakdown } from '@/lib/api/donor';
import type { PayoutFundingBreakdownDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, values?: Record<string, unknown>) =>
    values ? `${key}:${JSON.stringify(values)}` : key,
}));

vi.mock('@/lib/api/donor', () => ({
  getPayoutBreakdown: vi.fn(),
}));

const breakdown: PayoutFundingBreakdownDto = {
  payoutId: 'payout-1',
  payoutLabel: 'Achat de graines',
  payoutAmount: 500,
  myLines: [
    { donationId: 'don-1', confirmedAt: '2026-01-10T00:00:00Z', amount: 200 },
    { donationId: 'don-2', confirmedAt: '2026-01-05T00:00:00Z', amount: 100 },
  ],
  myTotal: 300,
  othersTotal: 200,
  othersDonationCount: 3,
};

describe('PayoutBreakdownModal', () => {
  it('renders nothing when payoutId is null', () => {
    const { container } = render(
      <PayoutBreakdownModal campaignId={null} payoutId={null} onClose={vi.fn()} onOpenTraceability={vi.fn()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('shows my own lines in full detail and the others aggregate when exposed', async () => {
    vi.mocked(getPayoutBreakdown).mockResolvedValue(breakdown);
    render(
      <PayoutBreakdownModal campaignId="camp-1" payoutId="payout-1" onClose={vi.fn()} onOpenTraceability={vi.fn()} />,
    );

    await waitFor(() => expect(screen.getByText(/Achat de graines/)).toBeInTheDocument());
    expect(screen.getAllByText(/200,00/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/100,00/).length).toBeGreaterThan(0);
    expect(screen.getByText(/others\.withAmount/)).toBeInTheDocument();
  });

  it('never shows an amount for other donors below the privacy floor, only a plain mention', async () => {
    vi.mocked(getPayoutBreakdown).mockResolvedValue({
      ...breakdown,
      othersTotal: null,
      othersDonationCount: 2,
    });
    render(
      <PayoutBreakdownModal campaignId="camp-1" payoutId="payout-1" onClose={vi.fn()} onOpenTraceability={vi.fn()} />,
    );

    await waitFor(() => expect(screen.getByText(/Achat de graines/)).toBeInTheDocument());
    expect(screen.queryByText(/others\.withAmount/)).not.toBeInTheDocument();
    expect(screen.getByText('others.hidden')).toBeInTheDocument();
  });

  it('shows no "others" mention at all when no other donor contributed', async () => {
    vi.mocked(getPayoutBreakdown).mockResolvedValue({
      ...breakdown,
      othersTotal: null,
      othersDonationCount: 0,
    });
    render(
      <PayoutBreakdownModal campaignId="camp-1" payoutId="payout-1" onClose={vi.fn()} onOpenTraceability={vi.fn()} />,
    );

    await waitFor(() => expect(screen.getByText(/Achat de graines/)).toBeInTheDocument());
    expect(screen.queryByText(/others\.withAmount/)).not.toBeInTheDocument();
    expect(screen.queryByText('others.hidden')).not.toBeInTheDocument();
  });

  it('calls onOpenTraceability with the right donationId when a line\'s icon is clicked', async () => {
    const onOpenTraceability = vi.fn();
    vi.mocked(getPayoutBreakdown).mockResolvedValue(breakdown);
    render(
      <PayoutBreakdownModal
        campaignId="camp-1"
        payoutId="payout-1"
        onClose={vi.fn()}
        onOpenTraceability={onOpenTraceability}
      />,
    );

    await waitFor(() => expect(screen.getByText(/Achat de graines/)).toBeInTheDocument());
    fireEvent.click(screen.getAllByRole('button', { name: /myLines\.viewTraceabilityAria/ })[0]);

    expect(onOpenTraceability).toHaveBeenCalledWith('don-1');
  });

  it('calls onClose when the close button is clicked', async () => {
    const onClose = vi.fn();
    vi.mocked(getPayoutBreakdown).mockResolvedValue(breakdown);
    render(
      <PayoutBreakdownModal campaignId="camp-1" payoutId="payout-1" onClose={onClose} onOpenTraceability={vi.fn()} />,
    );

    await waitFor(() => expect(screen.getByText(/Achat de graines/)).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText('close'));
    expect(onClose).toHaveBeenCalled();
  });
});
