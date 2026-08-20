import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
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

  it('does not call fetch immediately on input change (debounce)', () => {
    vi.useFakeTimers();
    render(<AssoSearch onSelect={vi.fn()} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Croix Rouge' } });
    expect(global.fetch).not.toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('displays API results after debounce delay', async () => {
    vi.useFakeTimers();
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({
        records: [
          {
            record: {
              fields: {
                numero_rna: 'W123456789',
                titre: 'Croix Rouge Française',
                typeavis: 'Création',
                commune_actuelle: 'PARIS',
                codepostal_actuel: '75008',
              },
            },
          },
        ],
      }),
    });

    render(<AssoSearch onSelect={vi.fn()} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Croix' } });

    await act(async () => {
      vi.advanceTimersByTime(400);
    });

    vi.useRealTimers();

    await waitFor(() => {
      expect(screen.getByText('Croix Rouge Française')).toBeInTheDocument();
    });
  });

  it('does not render the no-RNA escape hatch when onNoRna is omitted', () => {
    render(<AssoSearch onSelect={vi.fn()} />);
    expect(screen.queryByText('assoSearch.noRna')).not.toBeInTheDocument();
  });

  it('switches to manual entry when the no-RNA escape hatch is clicked', () => {
    const onNoRna = vi.fn();
    render(<AssoSearch onSelect={vi.fn()} onNoRna={onNoRna} />);

    fireEvent.click(screen.getByText('assoSearch.noRna'));

    expect(onNoRna).toHaveBeenCalledTimes(1);
  });
});
