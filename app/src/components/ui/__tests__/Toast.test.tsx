import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Toast } from '../Toast';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/stores/toastStore', () => ({
  useToastStore: (selector: (state: unknown) => unknown) =>
    selector({
      toasts: [{ id: '1', type: 'success', messageKey: 'errors.serverError' }],
      removeToast: vi.fn(),
    }),
}));

describe('Toast', () => {
  it('renders a toast with the correct role', () => {
    render(<Toast />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('displays the message key as translated text', () => {
    render(<Toast />);
    expect(screen.getByText('errors.serverError')).toBeInTheDocument();
  });
});
