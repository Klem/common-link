import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MagicLinkForm } from '../MagicLinkForm';
import { UserRole } from '@/types/auth';

vi.mock('next-intl', () => ({
  useTranslations:
    () =>
    (key: string, params?: Record<string, string>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
}));

describe('MagicLinkForm', () => {
  it('renders email input', () => {
    render(<MagicLinkForm onSubmit={vi.fn()} role={UserRole.ASSOCIATION} />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('submit button is disabled with invalid email', () => {
    render(<MagicLinkForm onSubmit={vi.fn()} role={UserRole.DONOR} />);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('submit button is enabled with valid email', () => {
    render(<MagicLinkForm onSubmit={vi.fn()} role={UserRole.DONOR} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test@example.com' } });
    expect(screen.getByRole('button')).not.toBeDisabled();
  });

  it('calls onSubmit with the entered email', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<MagicLinkForm onSubmit={onSubmit} role={UserRole.DONOR} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test@example.com' } });
    fireEvent.click(screen.getByRole('button'));
    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith('test@example.com');
    });
  });

  it('shows sent confirmation after successful submit', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<MagicLinkForm onSubmit={onSubmit} role={UserRole.DONOR} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test@example.com' } });
    fireEvent.click(screen.getByRole('button'));
    await waitFor(() => {
      expect(screen.getByText(/magicLink\.sent/)).toBeInTheDocument();
    });
  });
});
