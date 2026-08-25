import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { EmbedDonateReturnClient } from '../EmbedDonateReturnClient';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  getWidget: vi.fn(),
  getDonationStatus: vi.fn(),
  DonationReturnStatus: { PENDING: 'PENDING', CONFIRMED: 'CONFIRMED' },
}));

vi.mock('@/lib/gtm', () => ({
  pushDonationEvent: vi.fn(),
}));

import { getWidget, getDonationStatus } from '@/lib/api/public';
import { pushDonationEvent } from '@/lib/gtm';

const mockGetWidget = getWidget as ReturnType<typeof vi.fn>;
const mockGetDonationStatus = getDonationStatus as ReturnType<typeof vi.fn>;
const mockPushDonationEvent = pushDonationEvent as ReturnType<typeof vi.fn>;

const allowedWidget = { widgetAllowedOrigin: 'https://example.com' };

const sampleTracking = {
  ref: 'ref-123',
  amount: 25,
  currency: 'EUR',
  campaignId: 'camp-1',
  campaignName: 'Collecte de livres',
  associationName: 'Les Petits Écoliers',
  anonymous: false,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('EmbedDonateReturnClient', () => {
  describe('success path', () => {
    it('shows loading state initially while getWidget resolves', () => {
      mockGetWidget.mockReturnValue(new Promise(() => {}));
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page" tracking={null} />,
      );
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('shows confirmed UI when source origin matches allowlist', async () => {
      mockGetWidget.mockResolvedValue(allowedWidget);
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page?ref=widget" tracking={null} />,
      );
      await waitFor(() => expect(screen.getByText('confirmed.title')).toBeDefined());
      expect(screen.getByText('redirecting')).toBeDefined();
    });

    it('matches when the stored allowlist origin has a trailing slash', async () => {
      mockGetWidget.mockResolvedValue({ widgetAllowedOrigin: 'https://example.com/' });
      render(
        <EmbedDonateReturnClient
          widgetToken="clk_test"
          locale="fr"
          cancelled={false}
          source="https://example.com/some/deep/page.html"
          tracking={null}
        />,
      );
      await waitFor(() => expect(screen.getByText('confirmed.title')).toBeDefined());
      expect(screen.getByText('redirecting')).toBeDefined();
    });

    it('stays on loading when widgetAllowedOrigin is null', async () => {
      mockGetWidget.mockResolvedValue({ widgetAllowedOrigin: null });
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page" tracking={null} />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalled());
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('stays on loading when source origin does not match allowlist', async () => {
      mockGetWidget.mockResolvedValue(allowedWidget);
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://attacker.com/evil" tracking={null} />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalled());
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('stays on loading when source is null', () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source={null} tracking={null} />,
      );
      expect(screen.getByText('loading')).toBeDefined();
      expect(mockGetWidget).not.toHaveBeenCalled();
    });
  });

  describe('cancel path', () => {
    it('shows cancelled UI immediately', () => {
      mockGetWidget.mockReturnValue(new Promise(() => {}));
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source="https://example.com/page" tracking={null} />,
      );
      expect(screen.getByText('cancelled.title')).toBeDefined();
      expect(screen.getByText('cancelled.message')).toBeDefined();
    });

    it('calls getWidget to validate source on cancel', async () => {
      mockGetWidget.mockResolvedValue(allowedWidget);
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source="https://example.com/page" tracking={null} />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalledWith('clk_test'));
    });

    it('shows fallback link when source is null on cancel', () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source={null} tracking={null} />,
      );
      expect(screen.getByText('cancelled.title')).toBeDefined();
      const link = screen.getByRole('link');
      expect(link.getAttribute('href')).toBe('/fr/embed/donate/clk_test');
    });
  });

  describe('purchase tracking', () => {
    it('pushes the purchase event once getDonationStatus reports CONFIRMED', async () => {
      mockGetDonationStatus.mockResolvedValue({ status: 'CONFIRMED', method: 'creditcard' });
      render(
        <EmbedDonateReturnClient
          widgetToken="clk_test"
          locale="fr"
          cancelled={false}
          source={null}
          tracking={sampleTracking}
        />,
      );

      await waitFor(() => expect(mockPushDonationEvent).toHaveBeenCalledTimes(1));
      expect(mockPushDonationEvent).toHaveBeenCalledWith(
        'purchase',
        {
          transaction_id: 'ref-123',
          value: 25,
          currency: 'EUR',
          items: [{ item_id: 'camp-1', item_name: 'Collecte de livres' }],
          affiliation: 'Les Petits Écoliers',
        },
        { anonymous: false, paymentMethod: 'creditcard' },
      );
    });

    it('stops polling and never pushes if the payment stays PENDING through the whole window', async () => {
      vi.useFakeTimers();
      try {
        mockGetDonationStatus.mockResolvedValue({ status: 'PENDING' });
        render(
          <EmbedDonateReturnClient
            widgetToken="clk_test"
            locale="fr"
            cancelled={false}
            source={null}
            tracking={sampleTracking}
          />,
        );

        await vi.advanceTimersByTimeAsync(3000);

        expect(mockPushDonationEvent).not.toHaveBeenCalled();
        expect(mockGetDonationStatus.mock.calls.length).toBeGreaterThan(1);
      } finally {
        vi.useRealTimers();
      }
    });

    it('never polls or pushes when the payment is cancelled, even with tracking data', async () => {
      render(
        <EmbedDonateReturnClient
          widgetToken="clk_test"
          locale="fr"
          cancelled={true}
          source={null}
          tracking={sampleTracking}
        />,
      );

      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(mockGetDonationStatus).not.toHaveBeenCalled();
      expect(mockPushDonationEvent).not.toHaveBeenCalled();
    });

    it('never pushes purchase without tracking data, even on success', async () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source={null} tracking={null} />,
      );

      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(mockGetDonationStatus).not.toHaveBeenCalled();
      expect(mockPushDonationEvent).not.toHaveBeenCalled();
    });
  });
});
