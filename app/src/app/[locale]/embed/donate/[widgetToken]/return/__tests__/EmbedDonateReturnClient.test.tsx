import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { EmbedDonateReturnClient } from '../EmbedDonateReturnClient';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/public', () => ({
  getWidget: vi.fn(),
}));

import { getWidget } from '@/lib/api/public';

const mockGetWidget = getWidget as ReturnType<typeof vi.fn>;

const allowedWidget = { widgetAllowedOrigin: 'https://example.com' };

beforeEach(() => {
  vi.clearAllMocks();
});

describe('EmbedDonateReturnClient', () => {
  describe('success path', () => {
    it('shows loading state initially while getWidget resolves', () => {
      mockGetWidget.mockReturnValue(new Promise(() => {}));
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page" />,
      );
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('shows confirmed UI when source origin matches allowlist', async () => {
      mockGetWidget.mockResolvedValue(allowedWidget);
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page?ref=widget" />,
      );
      await waitFor(() => expect(screen.getByText('confirmed.title')).toBeDefined());
      expect(screen.getByText('redirecting')).toBeDefined();
    });

    it('stays on loading when widgetAllowedOrigin is null', async () => {
      mockGetWidget.mockResolvedValue({ widgetAllowedOrigin: null });
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://example.com/page" />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalled());
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('stays on loading when source origin does not match allowlist', async () => {
      mockGetWidget.mockResolvedValue(allowedWidget);
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source="https://attacker.com/evil" />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalled());
      expect(screen.getByText('loading')).toBeDefined();
    });

    it('stays on loading when source is null', () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={false} source={null} />,
      );
      expect(screen.getByText('loading')).toBeDefined();
      expect(mockGetWidget).not.toHaveBeenCalled();
    });
  });

  describe('cancel path', () => {
    it('shows cancelled UI immediately', () => {
      mockGetWidget.mockReturnValue(new Promise(() => {}));
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source="https://example.com/page" />,
      );
      expect(screen.getByText('cancelled.title')).toBeDefined();
      expect(screen.getByText('cancelled.message')).toBeDefined();
    });

    it('calls getWidget to validate source on cancel', async () => {
      mockGetWidget.mockResolvedValue(allowedWidget);
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source="https://example.com/page" />,
      );
      await waitFor(() => expect(mockGetWidget).toHaveBeenCalledWith('clk_test'));
    });

    it('shows fallback link when source is null on cancel', () => {
      render(
        <EmbedDonateReturnClient widgetToken="clk_test" locale="fr" cancelled={true} source={null} />,
      );
      expect(screen.getByText('cancelled.title')).toBeDefined();
      const link = screen.getByRole('link');
      expect(link.getAttribute('href')).toBe('/fr/embed/donate/clk_test');
    });
  });
});
