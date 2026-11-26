import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import HelloAssoLinkAttemptsDashboard from '../HelloAssoLinkAttemptsDashboard';
import { getHelloAssoLinkAttemptStats, listHelloAssoLinkAttempts } from '@/lib/api/admin';
import { HelloAssoLinkOutcome, type HelloAssoLinkAttemptStats } from '@/types/admin';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, values?: Record<string, unknown>) =>
    values ? `${key} ${JSON.stringify(values)}` : key,
  useLocale: () => 'fr',
}));

vi.mock('@/lib/api/admin', () => ({
  getHelloAssoLinkAttemptStats: vi.fn(),
  listHelloAssoLinkAttempts: vi.fn(),
}));

const stats: HelloAssoLinkAttemptStats = {
  from: '2026-10-27T00:00:00Z',
  to: '2026-11-26T00:00:00Z',
  total: 4,
  associations: 2,
  byOutcome: Object.values(HelloAssoLinkOutcome).map((outcome) => ({
    outcome,
    count: outcome === HelloAssoLinkOutcome.MATCHED ? 1 : outcome === HelloAssoLinkOutcome.NAME_MISMATCH ? 3 : 0,
  })),
};

const row = {
  id: 'a1',
  attemptedAt: '2026-11-25T15:04:00Z',
  associationId: 'asso-1',
  associationName: 'FOOTBALL CLUB DE MOUGINS',
  outcome: HelloAssoLinkOutcome.NAME_MISMATCH,
  clientIdMasked: '4cde…5647',
  organizationsCount: 1,
  organizationSlug: 'fc-mougins',
  organizationName: 'FC Mougins',
  organizationType: 'Association1901',
  organizationRna: null,
  associationRna: 'W061006975',
};

beforeEach(() => {
  vi.mocked(getHelloAssoLinkAttemptStats).mockReset().mockResolvedValue(stats);
  vi.mocked(listHelloAssoLinkAttempts)
    .mockReset()
    .mockResolvedValue({ content: [row], page: 0, size: 20, totalElements: 1, totalPages: 1 });
});

describe('HelloAssoLinkAttemptsDashboard', () => {
  it('shows the KPIs, success rate included', async () => {
    render(<HelloAssoLinkAttemptsDashboard />);

    expect(await screen.findByTestId('kpi-total')).toHaveTextContent('4');
    expect(screen.getByTestId('kpi-associations')).toHaveTextContent('2');
    expect(screen.getByTestId('kpi-success')).toHaveTextContent('percent {"value":25}');
  });

  it('draws only the outcomes that have attempts, and lists every outcome in the table', async () => {
    render(<HelloAssoLinkAttemptsDashboard />);

    expect(await screen.findByTestId('donut-NAME_MISMATCH')).toBeInTheDocument();
    expect(screen.getByTestId('donut-MATCHED')).toBeInTheDocument();
    expect(screen.queryByTestId('donut-RNA_MISMATCH')).not.toBeInTheDocument();
    Object.values(HelloAssoLinkOutcome).forEach((o) => {
      expect(screen.getByRole('button', { name: `outcome.${o}` })).toBeInTheDocument();
    });
  });

  it('lists the attempts with both RNAs, HelloAsso missing one shown as not provided', async () => {
    render(<HelloAssoLinkAttemptsDashboard />);

    const cell = await screen.findByText('FOOTBALL CLUB DE MOUGINS');
    const tr = cell.closest('tr')!;
    expect(within(tr).getByText('W061006975')).toBeInTheDocument();
    expect(within(tr).getByText('list.missingRna')).toBeInTheDocument();
    expect(within(tr).getByText(/Association1901/)).toBeInTheDocument();
  });

  it('filters the list by the outcome clicked in the legend, and clears it', async () => {
    render(<HelloAssoLinkAttemptsDashboard />);
    fireEvent.click(await screen.findByRole('button', { name: 'outcome.NAME_MISMATCH' }));

    await waitFor(() =>
      expect(listHelloAssoLinkAttempts).toHaveBeenLastCalledWith(
        expect.objectContaining({ outcome: HelloAssoLinkOutcome.NAME_MISMATCH, page: 0 }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: /list.clearFilter/ }));
    await waitFor(() =>
      expect(listHelloAssoLinkAttempts).toHaveBeenLastCalledWith(expect.objectContaining({ outcome: null })),
    );
  });

  it('reloads both endpoints over 7 days when that period is chosen', async () => {
    render(<HelloAssoLinkAttemptsDashboard />);
    await screen.findByTestId('kpi-total');
    fireEvent.click(screen.getByRole('button', { name: 'period {"days":7}' }));

    await waitFor(() => expect(getHelloAssoLinkAttemptStats).toHaveBeenCalledTimes(2));
    const { from, to } = vi.mocked(getHelloAssoLinkAttemptStats).mock.calls[1][0];
    expect(Date.parse(to) - Date.parse(from)).toBe(7 * 24 * 60 * 60 * 1000);
  });

  it('shows the empty state when the period has no attempt', async () => {
    vi.mocked(getHelloAssoLinkAttemptStats).mockResolvedValue({
      ...stats,
      total: 0,
      associations: 0,
      byOutcome: stats.byOutcome.map((c) => ({ ...c, count: 0 })),
    });
    render(<HelloAssoLinkAttemptsDashboard />);

    expect(await screen.findByText('empty')).toBeInTheDocument();
    expect(screen.getByTestId('kpi-success')).toHaveTextContent('none');
  });

  it('reports a loading failure', async () => {
    vi.mocked(getHelloAssoLinkAttemptStats).mockRejectedValue(new Error('down'));
    render(<HelloAssoLinkAttemptsDashboard />);

    expect(await screen.findByRole('alert')).toHaveTextContent('error');
  });
});
