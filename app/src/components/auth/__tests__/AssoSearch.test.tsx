import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AssoSearch } from '../AssoSearch';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

global.fetch = vi.fn();

describe('AssoSearch', () => {
  it('renders search input', () => {
    render(<AssoSearch onSelect={vi.fn()} />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('renders search hint text', () => {
    render(<AssoSearch onSelect={vi.fn()} />);
    expect(screen.getByText('assoSearch.searchHint')).toBeInTheDocument();
  });
});
