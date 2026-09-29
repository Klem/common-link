import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DonationHistoryTable } from '../DonationHistoryTable';
import type { DonorDonationDto } from '@/types/donor';

vi.mock('next-intl', () => ({
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
};

describe('DonationHistoryTable', () => {
  it('renders loading state', () => {
    render(
      <DonationHistoryTable donations={[]} isLoading error={null} onDownloadReceipt={vi.fn()} />,
    );
    expect(screen.getByText('loading')).toBeInTheDocument();
  });

  it('renders error state', () => {
    render(
      <DonationHistoryTable
        donations={[]}
        isLoading={false}
        error="error"
        onDownloadReceipt={vi.fn()}
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
        onDownloadReceipt={vi.fn()}
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
        onDownloadReceipt={vi.fn()}
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
        onDownloadReceipt={vi.fn()}
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
        onDownloadReceipt={vi.fn()}
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
      />,
    );
    fireEvent.click(screen.getAllByText('table.downloadReceipt')[0]);
    expect(onDownloadReceipt).toHaveBeenCalledWith(donation1);
  });
});
