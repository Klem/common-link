import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CookieConsentBanner } from '../CookieConsentBanner';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

beforeEach(() => {
  window.localStorage.clear();
  window.gtag = vi.fn();
});

describe('CookieConsentBanner', () => {
  it('renders nothing when no gtmId is configured', () => {
    const { container } = render(<CookieConsentBanner widgetToken="clk_abc" gtmId={null} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when a valid choice is already stored', () => {
    window.localStorage.setItem(
      'cl-consent-clk_abc',
      JSON.stringify({ choice: 'granted', timestamp: Date.now() }),
    );
    const { container } = render(<CookieConsentBanner widgetToken="clk_abc" gtmId="GTM-XXXX" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows the banner with equal accept/refuse buttons when no choice is stored', () => {
    render(<CookieConsentBanner widgetToken="clk_abc" gtmId="GTM-XXXX" />);
    expect(screen.getByText('accept')).toBeInTheDocument();
    expect(screen.getByText('refuse')).toBeInTheDocument();
  });

  it('accepting updates gtag consent to granted, persists the choice, and hides the banner', () => {
    render(<CookieConsentBanner widgetToken="clk_abc" gtmId="GTM-XXXX" />);
    fireEvent.click(screen.getByText('accept'));

    expect(window.gtag).toHaveBeenCalledWith('consent', 'update', {
      ad_storage: 'granted',
      ad_user_data: 'granted',
      ad_personalization: 'granted',
      analytics_storage: 'granted',
    });
    const stored = JSON.parse(window.localStorage.getItem('cl-consent-clk_abc')!);
    expect(stored.choice).toBe('granted');
    expect(screen.queryByText('accept')).not.toBeInTheDocument();
  });

  it('refusing updates gtag consent to denied and persists the choice', () => {
    render(<CookieConsentBanner widgetToken="clk_abc" gtmId="GTM-XXXX" />);
    fireEvent.click(screen.getByText('refuse'));

    expect(window.gtag).toHaveBeenCalledWith('consent', 'update', {
      ad_storage: 'denied',
      ad_user_data: 'denied',
      ad_personalization: 'denied',
      analytics_storage: 'denied',
    });
    const stored = JSON.parse(window.localStorage.getItem('cl-consent-clk_abc')!);
    expect(stored.choice).toBe('denied');
  });

  it('does not resurface the banner for an expired stored choice', () => {
    const thirteenMonthsAndADayMs = 13 * 30 * 24 * 60 * 60 * 1000 + 24 * 60 * 60 * 1000;
    window.localStorage.setItem(
      'cl-consent-clk_abc',
      JSON.stringify({ choice: 'granted', timestamp: Date.now() - thirteenMonthsAndADayMs }),
    );
    render(<CookieConsentBanner widgetToken="clk_abc" gtmId="GTM-XXXX" />);
    expect(screen.getByText('accept')).toBeInTheDocument();
  });
});
