import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { ActionPlacePicker } from '../ActionPlacePicker';
import { searchPlaces } from '@/lib/api/geo';
import type { ActionPlaceDto } from '@/types/campaign';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, values?: Record<string, string>) =>
    values?.label ? `${key}:${values.label}` : key,
  useLocale: () => 'fr',
}));

vi.mock('@/lib/api/geo', () => ({
  searchPlaces: vi.fn(),
}));

const vallauris: ActionPlaceDto = {
  type: 'COMMUNE', code: '06155', label: 'Vallauris (06)', scope: 'LOCALE', latitude: 43.58, longitude: 7.05,
};

describe('ActionPlacePicker', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the saved place and its derived scope', () => {
    render(<ActionPlacePicker value={vallauris} onChange={vi.fn()} />);
    expect(screen.getByText(/current:Vallauris \(06\)/)).toBeInTheDocument();
    expect(screen.getByText(/scope\.LOCALE/)).toBeInTheDocument();
  });

  it('searches communes after a debounce and sends the picked code', async () => {
    vi.mocked(searchPlaces).mockResolvedValue([{ type: 'COMMUNE', code: '06155', label: 'Vallauris (06)' }]);
    const onChange = vi.fn();
    render(<ActionPlacePicker value={null} onChange={onChange} />);

    fireEvent.change(screen.getByPlaceholderText('searchPlaceholder'), { target: { value: 'Vallau' } });
    expect(searchPlaces).not.toHaveBeenCalled();
    await act(async () => { await vi.advanceTimersByTimeAsync(300); });

    expect(searchPlaces).toHaveBeenCalledWith('Vallau');
    fireEvent.click(screen.getByRole('button', { name: 'Vallauris (06)' }));
    expect(onChange).toHaveBeenCalledWith({ type: 'COMMUNE', code: '06155' });
  });

  it('reports an unavailable search without sending anything', async () => {
    vi.mocked(searchPlaces).mockRejectedValue(new Error('down'));
    const onChange = vi.fn();
    render(<ActionPlacePicker value={null} onChange={onChange} />);

    fireEvent.change(screen.getByPlaceholderText('searchPlaceholder'), { target: { value: 'Paris' } });
    await act(async () => { await vi.advanceTimersByTimeAsync(300); });

    expect(screen.getByText('searchError')).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('sends a foreign country code, and never offers France or an overseas department', () => {
    const onChange = vi.fn();
    render(<ActionPlacePicker value={null} onChange={onChange} />);
    fireEvent.click(screen.getByLabelText('type.PAYS', { selector: 'input' }));

    const select = screen.getByLabelText('type.PAYS', { selector: 'select' });
    const codes = Array.from(select.querySelectorAll('option')).map((o) => o.getAttribute('value'));
    expect(codes).not.toContain('FR');
    expect(codes).not.toContain('RE');

    fireEvent.change(select, { target: { value: 'SN' } });
    expect(onChange).toHaveBeenCalledWith({ type: 'PAYS', code: 'SN' });
  });
});
