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
  localStorage.clear();
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

  it('shows failed state when paymentId is null and localStorage is empty', async () => {
    render(<EmbedDonateReturnClient {...defaultProps} paymentId={null} />);
    await waitFor(() => {
      expect(screen.getByText('failed.title')).toBeDefined();
    });
    expect(mockGetStatus).not.toHaveBeenCalled();
  });

  it('polls using paymentId from localStorage when prop is null', async () => {
    localStorage.setItem('widget_payment_clk_test', 'tr_from_storage');
    mockGetStatus.mockResolvedValue({ status: 'CONFIRMED' });
    render(<EmbedDonateReturnClient {...defaultProps} paymentId={null} />);
    await waitFor(() => {
      expect(screen.getByText('confirmed.title')).toBeDefined();
    });
    expect(mockGetStatus).toHaveBeenCalledWith('tr_from_storage');
  });

  it('uses sourceSite from localStorage as back button href', async () => {
    localStorage.setItem('widget_source_clk_test', 'http://192.168.1.11/path/to/widget');
    mockGetStatus.mockResolvedValue({ status: 'CONFIRMED' });
    render(<EmbedDonateReturnClient {...defaultProps} />);
    await waitFor(() => {
      const link = screen.getByRole('link', { name: 'retry' });
      expect(link.getAttribute('href')).toBe('http://192.168.1.11/path/to/widget');
    });
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
