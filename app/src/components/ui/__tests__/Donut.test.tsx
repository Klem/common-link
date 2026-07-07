import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Donut } from '../Donut';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const slices = [
  { label: 'Salaires', value: 5000, color: '#4ECDC4' },
  { label: 'Matériel', value: 2000, color: '#FF6B5B' },
  { label: 'Transport', value: 1000, color: '#FFB347' },
];

describe('Donut', () => {
  it('renders SVG paths for each slice', () => {
    const { container } = render(<Donut slices={slices} />);
    const paths = container.querySelectorAll('path');
    expect(paths).toHaveLength(3);
  });

  it('renders legend items', () => {
    render(<Donut slices={slices} />);
    expect(screen.getByText('Salaires')).toBeInTheDocument();
    expect(screen.getByText('Matériel')).toBeInTheDocument();
    expect(screen.getByText('Transport')).toBeInTheDocument();
  });

  it('shows percentages summing to approximately 100', () => {
    render(<Donut slices={slices} />);
    const pcts = screen.getAllByText(/\d+%/);
    const sum = pcts.reduce((acc, el) => acc + parseInt(el.textContent ?? '0'), 0);
    // Rounding can cause ±1 off-by-one across slices
    expect(sum).toBeGreaterThanOrEqual(99);
    expect(sum).toBeLessThanOrEqual(101);
  });

  it('shows empty state when all values are zero', () => {
    render(<Donut slices={[{ label: 'A', value: 0 }]} />);
    expect(screen.getByText('reporting.tab.donutEmpty')).toBeInTheDocument();
  });

  it('shows empty state when slices array is empty', () => {
    render(<Donut slices={[]} />);
    expect(screen.getByText('reporting.tab.donutEmpty')).toBeInTheDocument();
  });

  it('updates centre text on hover', () => {
    const { container } = render(<Donut slices={slices} />);
    const firstPath = container.querySelector('path')!;
    fireEvent.mouseEnter(firstPath);
    // Centre text should show pct% for the first slice (62%)
    expect(container.querySelector('text')?.textContent).toMatch(/\d+%/);
  });
});
