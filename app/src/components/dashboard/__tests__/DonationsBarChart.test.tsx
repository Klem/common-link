import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { DonationsBarChart } from '../DonationsBarChart';
import type { MonthlyPoint } from '@/types/association';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

function makeData(amounts: number[]): MonthlyPoint[] {
  return amounts.map((amount, i) => ({
    month: `2026-${String(i + 1).padStart(2, '0')}`,
    amount,
  }));
}

describe('DonationsBarChart', () => {
  it('renders 6 bar items', () => {
    const data = makeData([10, 20, 30, 40, 50, 60]);
    const { container } = render(<DonationsBarChart data={data} />);
    // Each month renders a div with a bar and a label span
    const labels = container.querySelectorAll('span');
    expect(labels.length).toBe(6);
  });

  it('renders with all-zero data without crashing', () => {
    const data = makeData([0, 0, 0, 0, 0, 0]);
    const { container } = render(<DonationsBarChart data={data} />);
    // 6 month label spans should still be rendered
    const spans = container.querySelectorAll('span');
    expect(spans.length).toBe(6);
  });

  it('shows chart title', () => {
    const data = makeData([100, 200, 150, 80, 60, 120]);
    const { getByText } = render(<DonationsBarChart data={data} />);
    expect(getByText('title')).toBeInTheDocument();
  });
});
