import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { ReceiptsTab } from '../ReceiptsTab';
import { getDonorReceiptYears, downloadAnnualReceiptsSummary } from '@/lib/api/donor';
import type { DonorReceiptYearDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/donor', () => ({
  getDonorReceiptYears: vi.fn(),
  downloadAnnualReceiptsSummary: vi.fn(),
}));

// jsdom does not implement the Blob URL APIs used by the download handler.
Object.defineProperty(global, 'URL', {
  value: { createObjectURL: vi.fn(() => 'blob:http://localhost/abc'), revokeObjectURL: vi.fn() },
  writable: true,
});

const years: DonorReceiptYearDto[] = [
  { year: 2026, donationCount: 2, totalAmount: 150, estimatedDeduction: 99 },
  { year: 2025, donationCount: 1, totalAmount: 100, estimatedDeduction: 66 },
];

describe('ReceiptsTab', () => {
  it('renders the annual list with correct stats', async () => {
    vi.mocked(getDonorReceiptYears).mockResolvedValue(years);
    render(<ReceiptsTab />);

    await waitFor(() => expect(screen.getByText('2026')).toBeInTheDocument());
    expect(screen.getByText('2025')).toBeInTheDocument();
    // Cumulative stats: 150 + 100 = 250 total, 99 + 66 = 165 deduction.
    expect(screen.getByText(/250,00\s?€/)).toBeInTheDocument();
    expect(screen.getByText(/165,00\s?€/)).toBeInTheDocument();
  });

  it('shows an empty state when there is no receipted donation', async () => {
    vi.mocked(getDonorReceiptYears).mockResolvedValue([]);
    render(<ReceiptsTab />);

    await waitFor(() => expect(screen.getByText('empty.title')).toBeInTheDocument());
  });

  it('triggers the PDF download for a given year', async () => {
    vi.mocked(getDonorReceiptYears).mockResolvedValue(years);
    vi.mocked(downloadAnnualReceiptsSummary).mockResolvedValue(new Blob(['pdf']));
    render(<ReceiptsTab />);

    await waitFor(() => expect(screen.getByText('2026')).toBeInTheDocument());
    fireEvent.click(screen.getAllByText('list.download')[0]);

    await waitFor(() => expect(downloadAnnualReceiptsSummary).toHaveBeenCalledWith(2026));
  });
});
