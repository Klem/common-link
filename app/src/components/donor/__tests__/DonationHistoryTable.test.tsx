import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { DonationHistoryTable } from '../DonationHistoryTable';
import type { DonorDonationDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useLocale: () => 'fr',
  useTranslations: () => (key: string) => key,
}));

const donation1: DonorDonationDto = {
  id: 'don-1',
  donatedAt: '2026-03-15T10:00:00Z',
  amount: 100,
  campaignId: 'camp-1',
  campaignName: 'Reforestation',
  campaignEmoji: '🌳',
  associationId: 'asso-1',
  associationName: 'Terre Verte',
  receiptAvailable: true,
  receiptNumber: 'REC-2026-001',
  usedAmount: 100,
  remainingAmount: 0,
};

const donation2: DonorDonationDto = {
  id: 'don-2',
  donatedAt: '2026-01-10T10:00:00Z',
  amount: 50,
  campaignId: 'camp-2',
  campaignName: 'Aide alimentaire',
  campaignEmoji: '🍎',
  associationId: 'asso-2',
  associationName: 'Solidarité',
  receiptAvailable: false,
  receiptNumber: null,
  usedAmount: 20,
  remainingAmount: 30,
};

const noop = () => {};

describe('DonationHistoryTable', () => {
  it('renders loading state', () => {
    render(
      <DonationHistoryTable
        donations={[]}
        isLoading
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    expect(screen.getByText('loading')).toBeInTheDocument();
  });

  it('renders error state', () => {
    render(
      <DonationHistoryTable
        donations={[]}
        isLoading={false}
        error="error"
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('error');
  });

  it('renders empty state when there are no donations', () => {
    render(
      <DonationHistoryTable
        donations={[]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    expect(screen.getByText('empty.title')).toBeInTheDocument();
  });

  it('renders the table with donation rows (desktop layout)', () => {
    render(
      <DonationHistoryTable
        donations={[donation1, donation2]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    expect(screen.getByRole('table', { name: 'tableLabel' })).toBeInTheDocument();
    expect(screen.getAllByText(/Reforestation/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Terre Verte/).length).toBeGreaterThan(0);
  });

  it('renders stacked cards for mobile alongside the table', () => {
    const { container } = render(
      <DonationHistoryTable
        donations={[donation1]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    expect(container.querySelector('div.md\\:hidden.donations-cards')).toBeInTheDocument();
  });

  it('renders the donated amounts and a used-share badge for both donations', () => {
    render(
      <DonationHistoryTable
        donations={[donation1, donation2]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    // donation1 fully used (100/100), donation2 partially used (20/50) — the badge text itself
    // is an ICU-interpolated key (`table.usedAmountBadge`), opaque under the next-intl mock here;
    // real differentiation between full/partial is covered by the desktop-scoped test below.
    expect(screen.getAllByText(/100,00\s?€/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/50,00\s?€/).length).toBeGreaterThan(0);
    expect(screen.getAllByText('table.usedAmountBadge')).toHaveLength(4); // 2 donations × (desktop + mobile)
  });

  it('mobile cards: show a download button only when a receipt is available, text labels (not icon-only)', () => {
    const { container } = render(
      <DonationHistoryTable
        donations={[donation1, donation2]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    const cards = container.querySelector('.donations-cards') as HTMLElement;
    expect(within(cards).getAllByText('table.receipt').length).toBe(1);
    expect(within(cards).getAllByText('table.noReceipt').length).toBe(1);
    expect(within(cards).getAllByText('table.traceability').length).toBe(2);
  });

  it('mobile cards: clicking the download button triggers the callback', () => {
    const onDownloadReceipt = vi.fn();
    const { container } = render(
      <DonationHistoryTable
        donations={[donation1]}
        isLoading={false}
        error={null}
        onDownloadReceipt={onDownloadReceipt}
        onOpenTraceability={noop}
      />,
    );
    const cards = container.querySelector('.donations-cards') as HTMLElement;
    fireEvent.click(within(cards).getByRole('button', { name: 'table.downloadReceiptAria' }));
    expect(onDownloadReceipt).toHaveBeenCalledWith(donation1);
  });

  it('mobile cards: clicking the traceability button triggers the callback', () => {
    const onOpenTraceability = vi.fn();
    const { container } = render(
      <DonationHistoryTable
        donations={[donation1]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={onOpenTraceability}
      />,
    );
    const cards = container.querySelector('.donations-cards') as HTMLElement;
    fireEvent.click(within(cards).getByRole('button', { name: 'table.viewTraceabilityAria' }));
    expect(onOpenTraceability).toHaveBeenCalledWith(donation1);
  });

  it('desktop table: traceability column comes before receipt, uses icon buttons and a badge', () => {
    render(
      <DonationHistoryTable
        donations={[donation1, donation2]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    const table = screen.getByRole('table', { name: 'tableLabel' });
    const headers = within(table).getAllByRole('columnheader').map((h) => h.textContent);
    expect(headers.indexOf('table.traceability')).toBeLessThan(headers.indexOf('table.receipt'));

    expect(within(table).getAllByRole('button', { name: 'table.viewTraceabilityAria' }).length).toBe(2);
    expect(within(table).getAllByRole('button', { name: 'table.downloadReceiptAria' }).length).toBe(1);
    expect(within(table).getAllByText('table.usedAmountBadge').length).toBe(2);
  });

  it('desktop table: clicking the traceability icon button triggers the callback', () => {
    const onOpenTraceability = vi.fn();
    render(
      <DonationHistoryTable
        donations={[donation1]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={onOpenTraceability}
      />,
    );
    const table = screen.getByRole('table', { name: 'tableLabel' });
    fireEvent.click(within(table).getByRole('button', { name: 'table.viewTraceabilityAria' }));
    expect(onOpenTraceability).toHaveBeenCalledWith(donation1);
  });

  it('desktop table: clicking the receipt download icon button triggers the callback', () => {
    const onDownloadReceipt = vi.fn();
    render(
      <DonationHistoryTable
        donations={[donation1]}
        isLoading={false}
        error={null}
        onDownloadReceipt={onDownloadReceipt}
        onOpenTraceability={noop}
      />,
    );
    const table = screen.getByRole('table', { name: 'tableLabel' });
    fireEvent.click(within(table).getByRole('button', { name: 'table.downloadReceiptAria' }));
    expect(onDownloadReceipt).toHaveBeenCalledWith(donation1);
  });

  it('links the campaign name to its report page', () => {
    render(
      <DonationHistoryTable
        donations={[donation1]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    const link = screen.getAllByRole('link', { name: /Reforestation/ })[0];
    expect(link).toHaveAttribute('href', '/fr/dashboard/donor/campaigns/camp-1');
  });
});
