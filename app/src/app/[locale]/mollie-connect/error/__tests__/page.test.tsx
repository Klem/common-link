import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import MollieConnectErrorPage from '../page';

vi.mock('next-intl', () => {
  const t = (key: string, values?: Record<string, unknown>) => (values ? `${key} ${JSON.stringify(values)}` : key);
  return { useTranslations: () => t };
});

describe('Mollie OAuth popup — error page', () => {
  const postMessage = vi.fn();
  const close = vi.spyOn(window, 'close').mockImplementation(() => undefined);

  beforeEach(() => {
    vi.useFakeTimers();
    postMessage.mockReset();
    close.mockClear();
    Object.defineProperty(window, 'opener', { value: { postMessage }, configurable: true });
  });
  afterEach(() => {
    vi.useRealTimers();
    window.history.replaceState({}, '', '/');
  });

  it('notifies the opener, shows a 5 second countdown, then closes the popup', async () => {
    render(<MollieConnectErrorPage />);

    expect(postMessage).toHaveBeenCalledWith({ type: 'MOLLIE_KYC_ERROR' }, window.location.origin);
    expect(screen.getByText('closing {"seconds":5}')).toBeInTheDocument();
    expect(screen.getByText('retry')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();

    // One tick per render: each second re-arms the timer from an effect.
    for (let i = 0; i < 4; i++) await act(async () => { vi.advanceTimersByTime(1000); });
    expect(close).not.toHaveBeenCalled();
    expect(screen.getByText('closing {"seconds":1}')).toBeInTheDocument();

    await act(async () => { vi.advanceTimersByTime(1000); });
    expect(close).toHaveBeenCalled();
  });

  it('explains that the Mollie organization is already linked to another association', () => {
    window.history.replaceState({}, '', '/?reason=organization-already-linked');
    render(<MollieConnectErrorPage />);

    expect(screen.getByText('organizationAlreadyLinked')).toBeInTheDocument();
    expect(screen.queryByText('retry')).not.toBeInTheDocument();
  });
});
