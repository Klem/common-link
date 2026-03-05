import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SetPasswordForm } from '../SetPasswordForm';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('SetPasswordForm', () => {
  it('renders password and confirm fields', () => {
    render(<SetPasswordForm onSubmit={vi.fn()} onSkip={vi.fn()} />);
    expect(screen.getByLabelText(/setPassword\.password\.label/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/setPassword\.confirm\.label/i)).toBeInTheDocument();
  });

  it('calls onSkip when skip button is clicked', () => {
    const onSkip = vi.fn();
    render(<SetPasswordForm onSubmit={vi.fn()} onSkip={onSkip} />);
    fireEvent.click(screen.getByRole('button', { name: /setPassword\.skip/i }));
    expect(onSkip).toHaveBeenCalled();
  });

  it('shows mismatch error when passwords differ', async () => {
    render(<SetPasswordForm onSubmit={vi.fn()} onSkip={vi.fn()} />);
    fireEvent.change(screen.getByLabelText(/setPassword\.password\.label/i), {
      target: { value: 'password123' },
    });
    fireEvent.change(screen.getByLabelText(/setPassword\.confirm\.label/i), {
      target: { value: 'different456' },
    });
    await waitFor(() => {
      expect(screen.getByText('errors.passwordMismatch')).toBeInTheDocument();
    });
  });

  it('calls onSubmit with the password when both fields match', async () => {
    const onSubmit = vi.fn();
    render(<SetPasswordForm onSubmit={onSubmit} onSkip={vi.fn()} />);
    fireEvent.change(screen.getByLabelText(/setPassword\.password\.label/i), {
      target: { value: 'securepass1' },
    });
    fireEvent.change(screen.getByLabelText(/setPassword\.confirm\.label/i), {
      target: { value: 'securepass1' },
    });
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /setPassword\.submit/i })).not.toBeDisabled();
    });
    fireEvent.click(screen.getByRole('button', { name: /setPassword\.submit/i }));
    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith('securepass1');
    });
  });
});
