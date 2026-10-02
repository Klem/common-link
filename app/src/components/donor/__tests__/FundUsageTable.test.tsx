import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { FundUsageTable } from '../FundUsageTable';
import type { SectionVariance } from '@/types/reporting';
import type { CampaignPayoutLineDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const sections: SectionVariance[] = [
  { sectionCode: '60', sectionName: 'Achats', planned: 1200, actual: 500, variance: -700 },
  { sectionCode: '64', sectionName: 'Salaires', planned: 2000, actual: 2500, variance: 500 },
];

const payouts: CampaignPayoutLineDto[] = [
  { payoutId: 'p-1', label: 'Achat de graines', amount: 500, payeeName: 'Pépinière du Nord', confirmedAt: '2026-02-05T00:00:00Z', sectionCode: '60' },
  { payoutId: 'p-2', label: 'Salaire coordinateur', amount: 2500, payeeName: 'J. Martin', confirmedAt: '2026-02-10T00:00:00Z', sectionCode: '64' },
];

describe('FundUsageTable', () => {
  it('renders one row per section', () => {
    render(<FundUsageTable sections={sections} payouts={payouts} onViewBreakdown={vi.fn()} />);
    expect(screen.getByText('Achats')).toBeInTheDocument();
    expect(screen.getByText('Salaires')).toBeInTheDocument();
  });

  it('shows the remaining amount as planned minus actual, including negative overspend', () => {
    render(<FundUsageTable sections={sections} payouts={payouts} onViewBreakdown={vi.fn()} />);
    // Achats: 1200 - 500 = 700 remaining
    expect(screen.getByText('700,00 €')).toBeInTheDocument();
    // Salaires: 2000 - 2500 = -500 (overspent)
    expect(screen.getByText('-500,00 €')).toBeInTheDocument();
  });

  it('keeps only one section expanded at a time', () => {
    render(<FundUsageTable sections={sections} payouts={payouts} onViewBreakdown={vi.fn()} />);

    const toggles = screen.getAllByRole('button', { name: /funds.showDetail/ });
    expect(toggles).toHaveLength(2);

    fireEvent.click(toggles[0]);
    expect(screen.getByText(/Achat de graines/)).toBeInTheDocument();
    expect(screen.queryByText(/Salaire coordinateur/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /funds.showDetail/ }));
    expect(screen.queryByText(/Achat de graines/)).not.toBeInTheDocument();
    expect(screen.getByText(/Salaire coordinateur/)).toBeInTheDocument();
  });

  it('sorts rows by a column when its header is clicked, toggling direction on repeat clicks', () => {
    const { container } = render(<FundUsageTable sections={sections} payouts={payouts} onViewBreakdown={vi.fn()} />);

    // Header buttons in column order: Catégorie, Prévu, Dépensé, Restant (the chevron column has none).
    // An aria-label overrides a button's visible text as its accessible name, so disambiguate by
    // DOM order instead of by name.
    const headerButtons = within(container.querySelector('thead')!).getAllByRole('button');
    const spentSortButton = headerButtons[2];

    const rowsText = () => screen.getAllByRole('row').slice(1).map((r) => r.textContent ?? '');
    expect(rowsText()[0]).toContain('Achats'); // default order: ascending by spent already (500 < 2500)

    fireEvent.click(spentSortButton); // 1st click: ascending (no visible change)
    expect(rowsText()[0]).toContain('Achats');

    fireEvent.click(spentSortButton); // 2nd click: descending
    expect(rowsText()[0]).toContain('Salaires');
  });

  it('calls onViewBreakdown with the payout id when "voir la répartition" is clicked in the detail', () => {
    const onViewBreakdown = vi.fn();
    render(<FundUsageTable sections={sections} payouts={payouts} onViewBreakdown={onViewBreakdown} />);

    fireEvent.click(screen.getAllByRole('button', { name: /funds.showDetail/ })[0]);
    fireEvent.click(screen.getByText('funds.viewBreakdown'));

    expect(onViewBreakdown).toHaveBeenCalledWith('p-1');
  });

  it('renders the empty state when there are no sections', () => {
    render(<FundUsageTable sections={[]} payouts={[]} onViewBreakdown={vi.fn()} />);
    expect(screen.getByText('funds.empty')).toBeInTheDocument();
  });
});
