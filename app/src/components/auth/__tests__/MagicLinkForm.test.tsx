import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MagicLinkForm } from '../MagicLinkForm';

vi.mock('next-intl', () => ({
  useTranslations:
    () =>
    (key: string, params?: Record<string, string>) =>
      params ? `${key}:${JSON.stringify(params)}` : key,
}));

describe('MagicLinkForm', () => {
  it('renders email input', () => {
    render(<MagicLinkForm onSubmit={vi.fn()} role="ASSOCIATION" />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('submit button is disabled with invalid email', () => {
    render(<MagicLinkForm onSubmit={vi.fn()} role="DONOR" />);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('submit button is enabled with valid email', () => {
    render(<MagicLinkForm onSubmit={vi.fn()} role="DONOR" />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test@example.com' } });
    expect(screen.getByRole('button')).not.toBeDisabled();
  });
});
