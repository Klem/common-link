import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { VarianceTable } from '../VarianceTable';
import type { SectionVariance } from '@/types/reporting';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const sections: SectionVariance[] = [
  { sectionCode: '60', sectionName: 'Matières premières', planned: 5000, actual: 6000, variance: 1000 },
  { sectionCode: '64', sectionName: 'Salaires', planned: 8000, actual: 7500, variance: -500 },
];

describe('VarianceTable', () => {
  it('renders a row per section', () => {
    render(<VarianceTable sections={sections} isCharges={true} />);
    expect(screen.getByText('Matières premières')).toBeInTheDocument();
    expect(screen.getByText('Salaires')).toBeInTheDocument();
  });

  it('shows column headers via i18n keys', () => {
    render(<VarianceTable sections={sections} isCharges={true} />);
    expect(screen.getByText('reporting.tab.colPlanned')).toBeInTheDocument();
    expect(screen.getByText('reporting.tab.colActual')).toBeInTheDocument();
    expect(screen.getByText('reporting.tab.colVariance')).toBeInTheDocument();
  });

  it('renders empty state when sections is empty', () => {
    render(<VarianceTable sections={[]} isCharges={true} />);
    expect(screen.getByText('reporting.tab.empty')).toBeInTheDocument();
  });

  it('applies coral colour for positive variance on charges', () => {
    const { container } = render(<VarianceTable sections={[sections[0]]} isCharges={true} />);
    const td = container.querySelectorAll('td')[3];
    expect(td.getAttribute('style')).toContain('var(--color-coral)');
  });

  it('applies green colour for negative variance on charges', () => {
    const { container } = render(<VarianceTable sections={[sections[1]]} isCharges={true} />);
    const td = container.querySelectorAll('td')[3];
    expect(td.getAttribute('style')).toContain('var(--color-green)');
  });

  it('applies green colour for positive variance on produits', () => {
    const { container } = render(<VarianceTable sections={[sections[0]]} isCharges={false} />);
    const td = container.querySelectorAll('td')[3];
    expect(td.getAttribute('style')).toContain('var(--color-green)');
  });

  it('applies coral colour for negative variance on produits', () => {
    const { container } = render(<VarianceTable sections={[sections[1]]} isCharges={false} />);
    const td = container.querySelectorAll('td')[3];
    expect(td.getAttribute('style')).toContain('var(--color-coral)');
  });
});
