import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Divider } from '../Divider';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key === 'divider.or' ? 'ou' : key,
}));

describe('Divider', () => {
  it('renders default "ou" text', () => {
    render(<Divider />);
    expect(screen.getByText('ou')).toBeInTheDocument();
  });

  it('renders custom text when provided', () => {
    render(<Divider text="custom" />);
    expect(screen.getByText('custom')).toBeInTheDocument();
  });
});
