import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { EmbedDonateReturnClient } from '../EmbedDonateReturnClient';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  getDonationStatus: vi.fn(),
  DonationReturnStatus: { PENDING: 'PENDING', CONFIRMED: 'CONFIRMED' },
}));

import { getDonationStatus } from '@/lib/api/public';

const mockGetStatus = getDonationStatus as ReturnType<typeof vi.fn>;

const defaultProps = {
  paymentId: 'tr_test123',
  widgetToken: 'clk_test',
  locale: 'fr',
  _pollIntervalMs: 0,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('EmbedDonateReturnClient', () => {
  it('shows loading state initially while first poll is pending', () => {
    mockGetStatus.mockReturnValue(new Promise(() => {}));
    render(<EmbedDonateReturnClient {...defaultProps} />);
    expect(screen.getByText('loading')).toBeDefined();
  });

  it('shows confirmed state when API returns CONFIRMED', async () => {
    mockGetStatus.mockResolvedValue({ status: 'CONFIRMED' });
    render(<EmbedDonateReturnClient {...defaultProps} />);
    await waitFor(() => {
      expect(screen.getByText('confirmed.title')).toBeDefined();
    });
  });

  it('shows pending timeout state after max attempts still returning PENDING', async () => {
    mockGetStatus.mockResolvedValue({ status: 'PENDING' });
    render(<EmbedDonateReturnClient {...defaultProps} />);
    await waitFor(() => {
      expect(screen.getByText('pending.title')).toBeDefined();
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(5);
  });

  it('shows failed state when API throws', async () => {
    mockGetStatus.mockRejectedValue(new Error('Not Found'));
    render(<EmbedDonateReturnClient {...defaultProps} />);
    await waitFor(() => {
      expect(screen.getByText('failed.title')).toBeDefined();
    });
  });

  it('shows failed state immediately when paymentId is null', () => {
    render(<EmbedDonateReturnClient {...defaultProps} paymentId={null} />);
    expect(screen.getByText('failed.title')).toBeDefined();
    expect(mockGetStatus).not.toHaveBeenCalled();
  });

  it('shows retry button in PENDING_TIMEOUT state', async () => {
    mockGetStatus.mockResolvedValue({ status: 'PENDING' });
    render(<EmbedDonateReturnClient {...defaultProps} />);
    await waitFor(() => {
      expect(screen.getByText('retry')).toBeDefined();
    });
  });

  it('shows retry button in FAILED state', async () => {
    mockGetStatus.mockRejectedValue(new Error('Network error'));
    render(<EmbedDonateReturnClient {...defaultProps} />);
    await waitFor(() => {
      expect(screen.getByText('retry')).toBeDefined();
    });
  });
});
