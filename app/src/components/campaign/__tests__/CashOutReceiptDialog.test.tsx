import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { CashOutReceiptDialog } from '../CashOutReceiptDialog';
import type { CampaignCashOut } from '@/types/campaignFunds';

vi.mock('next-intl', () => {
  const t = (key: string, values?: Record<string, unknown>) => (values ? `${key} ${JSON.stringify(values)}` : key);
  t.rich = (key: string, values: Record<string, unknown>) => `${key} ${JSON.stringify({ amount: values.amount })}`;
  return { useTranslations: () => t };
});

const addToast = vi.fn();
vi.mock('@/stores/toastStore', () => ({
  useToastStore: (selector: (s: { addToast: typeof addToast }) => unknown) => selector({ addToast }),
}));

const cashOut = (over: Partial<CampaignCashOut> = {}): CampaignCashOut => ({
  idCashOut: 60001, cashOutDate: '2026-11-10', netTotal: 1001, campaignAmount: 371, state: 'IN_TRANSIT',
  autoAvailableOn: '2026-11-12', confirmedAt: null, confirmedBy: null, ...over,
});

const refusal = (status: number, code?: string) =>
  Object.assign(new Error(String(status)), { isAxiosError: true, response: { status, data: code ? { code } : {} } });

describe('CashOutReceiptDialog', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows issuer, date, net amount, effect, rest for other campaigns and the automatic date', () => {
    render(<CashOutReceiptDialog cashOut={cashOut()} onClose={vi.fn()} onConfirm={vi.fn()} />);

    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByText('issuerValue')).toBeInTheDocument();
    expect(screen.getByText('issueDate')).toBeInTheDocument();
    expect(screen.getByText('1 001,00 €')).toBeInTheDocument();
    expect(screen.getByText(/effect/)).toHaveTextContent('371,00');
    expect(screen.getByText(/rest/)).toHaveTextContent('630,00');
    expect(screen.getByText(/^auto/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'notYet' })).toHaveFocus();
  });

  it('without a known net total: no statement amount and no rest', () => {
    render(<CashOutReceiptDialog cashOut={cashOut({ netTotal: null })} onClose={vi.fn()} onConfirm={vi.fn()} />);
    expect(screen.queryByText('statementAmount')).not.toBeInTheDocument();
    expect(screen.queryByText(/rest/)).not.toBeInTheDocument();
  });

  it('confirming calls the server once, toasts and closes', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    render(<CashOutReceiptDialog cashOut={cashOut()} onClose={onClose} onConfirm={onConfirm} />);

    fireEvent.click(screen.getByRole('button', { name: /confirm/ }));
    fireEvent.click(screen.getByRole('button', { name: /confirm/ }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledWith(60001);
    expect(addToast).toHaveBeenCalledWith('success', 'cashOutConfirmed');
  });

  it.each([
    [refusal(409, 'CASH_OUT_NOT_CASHED_OUT'), 'errors.notCashedOut'],
    [refusal(409, 'HELLOASSO_NOT_LINKED'), 'errors.notLinked'],
    [refusal(404), 'errors.notFound'],
    [refusal(502), 'errors.unavailable'],
  ])('a refusal is explained in the dialog, without toast', async (error, key) => {
    const onClose = vi.fn();
    render(<CashOutReceiptDialog cashOut={cashOut()} onClose={onClose} onConfirm={vi.fn().mockRejectedValue(error)} />);

    await act(async () => { fireEvent.click(screen.getByRole('button', { name: /confirm/ })); });

    expect(await screen.findByRole('alert')).toHaveTextContent(key);
    expect(onClose).not.toHaveBeenCalled();
    expect(addToast).not.toHaveBeenCalled();
  });

  it('Escape and the backdrop mean "not yet"', () => {
    const onClose = vi.fn();
    const { container } = render(<CashOutReceiptDialog cashOut={cashOut()} onClose={onClose} onConfirm={vi.fn()} />);
    fireEvent.keyDown(document, { key: 'Escape' });
    fireEvent.click(container.querySelector('.cm-ov')!);
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('is closed without a payout', () => {
    render(<CashOutReceiptDialog cashOut={null} onClose={vi.fn()} onConfirm={vi.fn()} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
