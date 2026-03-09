import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LoginProgressOverlay } from '../LoginProgressOverlay';

vi.mock('next-intl', () => ({
  useTranslations:
    () =>
    (key: string, params?: Record<string, string>) =>
      params ? `${key}(${Object.values(params).join(',')})` : key,
}));

describe('LoginProgressOverlay', () => {
  it('renders with fixed overlay class', () => {
    const { container } = render(<LoginProgressOverlay provider="google" />);
    expect(container.firstChild).toHaveClass('fixed');
  });

  it('renders custom steps when provided', () => {
    render(
      <LoginProgressOverlay provider="google" steps={['Step A', 'Step B', 'Step C', 'Step D']} />,
    );
    expect(screen.getByText('Step A')).toBeInTheDocument();
  });
});
