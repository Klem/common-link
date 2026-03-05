import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EmailPasswordForm } from '../EmailPasswordForm';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('EmailPasswordForm', () => {
  it('renders email and password inputs', () => {
    render(<EmailPasswordForm onSubmit={vi.fn()} />);
    expect(screen.getByLabelText(/login\.email\.label/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/login\.password\.label/i)).toBeInTheDocument();
  });

  it('submit button is initially disabled', () => {
    render(<EmailPasswordForm onSubmit={vi.fn()} />);
    expect(screen.getByRole('button', { name: /login\.submit/i })).toBeDisabled();
  });

  it('shows validation error for short password', async () => {
    render(<EmailPasswordForm onSubmit={vi.fn()} />);
    fireEvent.change(screen.getByLabelText(/login\.email\.label/i), {
      target: { value: 'test@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/login\.password\.label/i), {
      target: { value: 'short' },
    });
    await waitFor(() => {
      expect(screen.getByText('errors.passwordTooShort')).toBeInTheDocument();
    });
  });
});
