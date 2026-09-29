import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
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
    expect(container.querySelector('ul.md\\:hidden')).toBeInTheDocument();
  });

  it('shows a download button only when a receipt is available', () => {
    render(
      <DonationHistoryTable
        donations={[donation1, donation2]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    expect(screen.getAllByText('table.downloadReceipt').length).toBeGreaterThan(0);
    expect(screen.getAllByText('table.noReceipt').length).toBeGreaterThan(0);
  });

  it('triggers the receipt download callback when clicked', () => {
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
    fireEvent.click(screen.getAllByText('table.downloadReceipt')[0]);
    expect(onDownloadReceipt).toHaveBeenCalledWith(donation1);
  });

  it('renders the used-share amounts for partial and fully-used donations', () => {
    render(
      <DonationHistoryTable
        donations={[donation1, donation2]}
        isLoading={false}
        error={null}
        onDownloadReceipt={noop}
        onOpenTraceability={noop}
      />,
    );
    expect(screen.getAllByText(/100,00\s?€/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/20,00\s?€/).length).toBeGreaterThan(0);
  });

  it('triggers the traceability callback when clicked', () => {
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
    fireEvent.click(screen.getAllByText('table.viewTraceability')[0]);
    expect(onOpenTraceability).toHaveBeenCalledWith(donation1);
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
