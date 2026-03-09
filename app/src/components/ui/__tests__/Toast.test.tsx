import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { Toast } from '../Toast';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const removeToastMock = vi.hoisted(() => vi.fn());

let mockToasts: Array<{ id: string; type: 'success' | 'error' | 'warning'; messageKey: string }> =
  [];

vi.mock('@/stores/toastStore', () => ({
  useToastStore: (selector: (state: unknown) => unknown) =>
    selector({ toasts: mockToasts, removeToast: removeToastMock }),
}));

describe('Toast', () => {
  beforeEach(() => {
    mockToasts = [{ id: '1', type: 'success', messageKey: 'errors.serverError' }];
    removeToastMock.mockClear();
  });

  it('renders a toast with the correct role', () => {
    render(<Toast />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('displays the message key as translated text', () => {
    render(<Toast />);
    expect(screen.getByText('errors.serverError')).toBeInTheDocument();
  });

  it('calls removeToast after 5 seconds (auto-dismiss)', async () => {
    vi.useFakeTimers();
    render(<Toast />);
    await act(async () => {
      vi.advanceTimersByTime(5000);
    });
    expect(removeToastMock).toHaveBeenCalledWith('1');
    vi.useRealTimers();
  });

  it('renders success, error, and warning toasts with distinct border styles', () => {
    mockToasts = [
      { id: '1', type: 'success', messageKey: 'msg.success' },
      { id: '2', type: 'error', messageKey: 'msg.error' },
      { id: '3', type: 'warning', messageKey: 'msg.warning' },
    ];
    render(<Toast />);
    const alerts = screen.getAllByRole('alert');
    expect(alerts).toHaveLength(3);
    expect(alerts[0].className).toContain('border-green');
    expect(alerts[1].className).toContain('border-red');
    expect(alerts[2].className).toContain('border-yellow');
  });
});
