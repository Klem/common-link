import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import { EmbedDonateReturnClient, withDonationSuccessFlag } from '../EmbedDonateReturnClient';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  getWidget: vi.fn(),
  getDonationStatus: vi.fn(),
  DonationReturnStatus: { PENDING: 'PENDING', CONFIRMED: 'CONFIRMED' },
}));

import { getWidget, getDonationStatus } from '@/lib/api/public';

const mockGetWidget = getWidget as ReturnType<typeof vi.fn>;
const mockGetDonationStatus = getDonationStatus as ReturnType<typeof vi.fn>;

const allowedWidget = { widgetAllowedOrigin: 'https://example.com' };

beforeEach(() => {
  vi.clearAllMocks();
  window.dataLayer = [];
});

describe('EmbedDonateReturnClient', () => {
  describe('success path', () => {
    it('shows loading state initially while getWidget resolves', () => {
      mockGetWidget.mockReturnValue(new Promise(() => {}));
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page" donationRef={null} />,
      );
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('shows confirmed UI when source origin matches the campaign page origin', async () => {
      mockGetWidget.mockResolvedValue(allowedWidget);
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page?ref=widget" donationRef={null} />,
      );
      await waitFor(() => expect(screen.getByText('confirmed.title')).toBeDefined());
      expect(screen.getByText('redirecting')).toBeDefined();
    });

    it('accepts a source on the origin of a full campaign page URL', async () => {
      mockGetWidget.mockResolvedValue({ widgetAllowedOrigin: 'https://example.com/campagnes/ete?x=1' });
      render(
        <EmbedDonateReturnClient
          widgetToken="clk_test"
          locale="fr"
          cancelled={false}
          source="https://example.com/some/deep/page.html"
          donationRef={null}
        />,
      );
      await waitFor(() => expect(screen.getByText('confirmed.title')).toBeDefined());
    });

    it('accepts a stored bare origin with a trailing slash', async () => {
      mockGetWidget.mockResolvedValue({ widgetAllowedOrigin: 'https://example.com/' });
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page" donationRef={null} />,
      );
      await waitFor(() => expect(screen.getByText('confirmed.title')).toBeDefined());
    });

    it('stays on loading when widgetAllowedOrigin is null', async () => {
      mockGetWidget.mockResolvedValue({ widgetAllowedOrigin: null });
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page" donationRef={null} />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalled());
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('stays on loading when source origin does not match the campaign page origin', async () => {
      mockGetWidget.mockResolvedValue({ widgetAllowedOrigin: 'https://example.com/campagne' });
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://attacker.com/campagne" donationRef={null} />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalled());
      expect(screen.getByText('loading')).toBeDefined();
    });

    it("sends the donor back to this widget's own landing page opened directly, without the allowlist", async () => {
      render(
        <EmbedDonateReturnClient
          widgetToken="clk_test"
          locale="fr"
          cancelled={false}
          source={`${window.location.origin}/fr/lp/clk_test`}
          donationRef={null}
        />,
      );
      await waitFor(() => expect(screen.getByText('confirmed.title')).toBeDefined());
      expect(mockGetWidget).not.toHaveBeenCalled();
    });

    it("does not treat another widget's landing page as its own", async () => {
      mockGetWidget.mockResolvedValue({ widgetAllowedOrigin: 'https://example.com/campagne' });
      render(
        <EmbedDonateReturnClient
          widgetToken="clk_test"
          locale="fr"
          cancelled={false}
          source={`${window.location.origin}/fr/lp/clk_other`}
          donationRef={null}
        />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalled());
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('stays on loading when source is null', () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source={null} donationRef={null} />,
      );
      expect(screen.getByText('loading')).toBeDefined();
      expect(mockGetWidget).not.toHaveBeenCalled();
    });
  });

  describe('cancel path', () => {
    it('shows cancelled UI immediately', () => {
      mockGetWidget.mockReturnValue(new Promise(() => {}));
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source="https://example.com/page" donationRef={null} />,
      );
      expect(screen.getByText('cancelled.title')).toBeDefined();
      expect(screen.getByText('cancelled.message')).toBeDefined();
    });

    it('calls getWidget to validate source on cancel', async () => {
      mockGetWidget.mockResolvedValue(allowedWidget);
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source="https://example.com/page" donationRef={null} />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalledWith('clk_test'));
    });

    it('shows fallback link when source is null on cancel', () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source={null} donationRef={null} />,
      );
      expect(screen.getByText('cancelled.title')).toBeDefined();
      const link = screen.getByRole('link');
      expect(link.getAttribute('href')).toBe('/fr/embed/donate/clk_test');
    });

    it('never polls the status when the payment is cancelled', async () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source={null} donationRef="ref-123" />,
      );
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(mockGetDonationStatus).not.toHaveBeenCalled();
    });
  });

  describe('success flag on the redirect back', () => {
    const redirectTarget = async (status: { status: string; method?: string }) => {
      vi.useFakeTimers();
      // The component navigates `window.top` (breaking out of an iframe) — stand in for it.
      const fakeTop = { location: { href: '' } };
      vi.stubGlobal('top', fakeTop);
      try {
        mockGetWidget.mockResolvedValue(allowedWidget);
        mockGetDonationStatus.mockResolvedValue(status);
        render(
          <EmbedDonateReturnClient
            widgetToken="clk_test"
            locale="fr"
            cancelled={false}
            source="https://example.com/page?x=1"
            donationRef="ref-123"
          />,
        );
        // State updates only flush when `act` exits, and the 5s redirect timer is scheduled by
        // those updates — so time is advanced in passes: source validation, then the poll
        // window (30s, relevant when PENDING), then the redirect delay.
        await act(async () => { await vi.advanceTimersByTimeAsync(100); });
        await act(async () => { await vi.advanceTimersByTimeAsync(31000); });
        await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
        return fakeTop.location.href;
      } finally {
        vi.unstubAllGlobals();
        vi.useRealTimers();
      }
    };

    it('adds cl_donation=success and cl_ref once the server confirmed the payment', async () => {
      const target = new URL(await redirectTarget({ status: 'CONFIRMED', method: 'creditcard' }));
      expect(target.origin + target.pathname).toBe('https://example.com/page');
      expect(target.searchParams.get('x')).toBe('1');
      expect(target.searchParams.get('cl_donation')).toBe('success');
      expect(target.searchParams.get('cl_ref')).toBe('ref-123');
    });

    it('redirects without any flag when the payment was never confirmed', async () => {
      expect(await redirectTarget({ status: 'PENDING' })).toBe('https://example.com/page?x=1');
    });

    it('withDonationSuccessFlag leaves the URL untouched without a ref', () => {
      expect(withDonationSuccessFlag('https://example.com/p', null)).toBe('https://example.com/p');
    });
  });

  describe('no tracking on this page (RGPD)', () => {
    it('pushes nothing to the dataLayer, even once the payment is confirmed', async () => {
      mockGetDonationStatus.mockResolvedValue({ status: 'CONFIRMED', method: 'creditcard', amount: 25 });
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source={null} donationRef="ref-123" />,
      );
      await waitFor(() => expect(mockGetDonationStatus).toHaveBeenCalledWith('ref-123'));
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(window.dataLayer).toEqual([]);
    });

    it('never polls without a donation ref', async () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source={null} donationRef={null} />,
      );
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(mockGetDonationStatus).not.toHaveBeenCalled();
    });
  });
});
