import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { EmailRegisterForm } from '../EmailRegisterForm';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('EmailRegisterForm', () => {
  const defaultProps = {
    onSubmit: vi.fn(),
    submitLabel: 'Créer mon compte →',
  };

  it('renders email, password and confirm inputs', () => {
    render(<EmailRegisterForm {...defaultProps} />);
    expect(screen.getByLabelText(/signup\.emailPassword\.email\.label/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/signup\.emailPassword\.password\.label/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/signup\.emailPassword\.confirm\.label/i)).toBeInTheDocument();
  });

  it('renders the custom submitLabel', () => {
    render(<EmailRegisterForm {...defaultProps} submitLabel="Continuer →" />);
    expect(screen.getByRole('button', { name: 'Continuer →' })).toBeInTheDocument();
  });

  it('submit button is initially disabled', () => {
    render(<EmailRegisterForm {...defaultProps} />);
    expect(screen.getByRole('button', { name: defaultProps.submitLabel })).toBeDisabled();
  });

  it('shows validation error for invalid email', async () => {
    render(<EmailRegisterForm {...defaultProps} />);
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.email\.label/i), {
      target: { value: 'not-an-email' },
    });
    await waitFor(() => {
      expect(screen.getByText('errors.emailInvalid')).toBeInTheDocument();
    });
  });

  it('shows validation error for short password', async () => {
    render(<EmailRegisterForm {...defaultProps} />);
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.email\.label/i), {
      target: { value: 'test@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.password\.label/i), {
      target: { value: 'short' },
    });
    await waitFor(() => {
      expect(screen.getByText('errors.passwordTooShort')).toBeInTheDocument();
    });
  });

  it('shows mismatch error when passwords differ', async () => {
    render(<EmailRegisterForm {...defaultProps} />);
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.password\.label/i), {
      target: { value: 'password123' },
    });
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.confirm\.label/i), {
      target: { value: 'different456' },
    });
    await waitFor(() => {
      expect(screen.getByText('errors.passwordMismatch')).toBeInTheDocument();
    });
  });

  it('displays external error prop', () => {
    render(<EmailRegisterForm {...defaultProps} error="Un compte existe déjà avec cet email." />);
    expect(screen.getByText('Un compte existe déjà avec cet email.')).toBeInTheDocument();
  });

  it('submit button is disabled while loading', () => {
    render(<EmailRegisterForm {...defaultProps} loading />);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('calls onSubmit with email and password when form is valid', async () => {
    const onSubmit = vi.fn();
    render(<EmailRegisterForm {...defaultProps} onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.email\.label/i), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.password\.label/i), {
      target: { value: 'securepass1' },
    });
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.confirm\.label/i), {
      target: { value: 'securepass1' },
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: defaultProps.submitLabel })).not.toBeDisabled();
    });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: defaultProps.submitLabel }));
    });

    expect(onSubmit).toHaveBeenCalledWith('user@example.com', 'securepass1');
  });

  it('does not call onSubmit when passwords do not match', async () => {
    const onSubmit = vi.fn();
    render(<EmailRegisterForm {...defaultProps} onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.email\.label/i), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.password\.label/i), {
      target: { value: 'password123' },
    });
    fireEvent.change(screen.getByLabelText(/signup\.emailPassword\.confirm\.label/i), {
      target: { value: 'different456' },
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: defaultProps.submitLabel })).toBeDisabled();
    });

    expect(onSubmit).not.toHaveBeenCalled();
  });
});
