import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { IssuePaymentForm } from '../IssuePaymentForm';
import type { CampaignFunds } from '@/types/campaignFunds';
import type { PayeeDto } from '@/types/payee';

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key),
}));
const addToast = vi.fn();
vi.mock('@/stores/toastStore', () => ({
  useToastStore: (selector: (s: { addToast: typeof addToast }) => unknown) => selector({ addToast }),
}));
vi.mock('@/lib/api/payment', () => ({ getBlockingReasons: vi.fn().mockResolvedValue([]) }));
vi.mock('@/hooks/payee/usePayees');

import { usePayees } from '@/hooks/payee/usePayees';

const payee: PayeeDto = {
  id: 'payee-1', payeeType: 'COMPANY', name: 'ACME Corp', identifier1: '123456789', identifier2: null, activityCode: null,
  category: null, city: 'Paris', postalCode: '75001', active: true, hasPayouts: false,
  ibans: [{ id: 'iban-1', iban: 'FR76 0000 0000', status: 'VERIFIED', vopResult: null, vopSuggestedName: null, verifiedAt: null, active: true }],
  createdAt: '2026-01-01T00:00:00Z',
};

const funds = (over: Partial<CampaignFunds> = {}): CampaignFunds => ({
  provider: 'HELLOASSO', raised: 165, atProvider: 40, payoutPreparing: 0, inTransit: 30, available: 70, withdrawn: 0,
  paidOut: 50, pendingPayouts: 0, disbursable: 20, cashOuts: [], manualReleases: [], lastSyncAt: null,
  syncError: false, syncErrorCode: null, nextRefreshAvailableAt: null, ...over,
});

function renderForm(f: CampaignFunds | null, extra: Record<string, unknown> = {}) {
  const props = { campaignId: 'c1', submit: vi.fn(), isSaving: false, paymentsDisabled: false, funds: f, onFundsChanged: vi.fn(), ...extra };
  render(<IssuePaymentForm {...props} />);
  return props;
}

function fill(amount: string) {
  const selects = screen.getAllByRole('combobox');
  fireEvent.change(selects[1], { target: { value: 'payee-1' } });
  fireEvent.change(selects[0], { target: { value: '60-mat' } });
  fireEvent.change(screen.getByPlaceholderText('0,00'), { target: { value: amount } });
  fireEvent.change(screen.getByPlaceholderText('form.labelPlaceholder'), { target: { value: 'Achat de fournitures diverses' } });
}

const submitButton = () => screen.getByRole('button', { name: 'form.submit' });

describe('IssuePaymentForm with the campaign funds', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (usePayees as ReturnType<typeof vi.fn>).mockReturnValue({ payees: [payee] });
  });

  it('shows the amount usable under the amount', () => {
    renderForm(funds());
    expect(screen.getByText(/^available:/)).toHaveTextContent('20,00');
    expect(screen.getByPlaceholderText('0,00')).toHaveAttribute('aria-describedby', 'pay-amt-hint');
  });

  it('says it is computing while the funds load', () => {
    renderForm(null, { fundsLoading: true });
    expect(screen.getByText('computing')).toBeInTheDocument();
  });

  it('an amount above the usable one is flagged, mentions the transfers on their way, and cannot be sent', () => {
    renderForm(funds());
    fill('25');
    const hint = document.getElementById('pay-amt-hint')!;
    expect(hint).toHaveClass('err');
    expect(hint).toHaveTextContent('exceeds');
    expect(hint).toHaveTextContent('inTransit');
    expect(screen.getByPlaceholderText('0,00')).toHaveAttribute('aria-invalid', 'true');
    expect(submitButton()).toBeDisabled();
  });

  it('an amount within the usable one can be sent', async () => {
    renderForm(funds());
    fill('20');
    await waitFor(() => expect(submitButton()).not.toBeDisabled());
  });

  it('nothing usable: explained at the top (arrived but already used, part still at HelloAsso)', () => {
    renderForm(funds({ disbursable: 0 }));
    const block = screen.getByRole('status');
    expect(block).toHaveTextContent('none');
    expect(block).toHaveTextContent('noneUsedAtProvider');
    expect(block).toHaveTextContent('payWhenArrived');
  });

  it('nothing arrived yet: says the donations were not paid out', () => {
    renderForm(funds({ disbursable: 0, available: 0, atProvider: 0, inTransit: 0 }));
    expect(screen.getByRole('status')).toHaveTextContent('notPaidOut');
  });

  it('a Mollie association gets the first sentence only', () => {
    renderForm(funds({ provider: 'MOLLIE', disbursable: 0 }));
    expect(screen.getByRole('status')).toHaveTextContent(/^none$/);
  });

  it('a 409 FUNDS_NOT_AVAILABLE is explained with its figures, without the payment error toast', async () => {
    const refusal = Object.assign(new Error('409'), {
      isAxiosError: true,
      response: { status: 409, data: { code: 'FUNDS_NOT_AVAILABLE', disbursable: 5, inTransit: 30, atProvider: 40 } },
    });
    const props = renderForm(funds(), { submit: vi.fn().mockRejectedValue(refusal) });
    fill('20');
    await waitFor(() => expect(submitButton()).not.toBeDisabled());
    fireEvent.click(submitButton());
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'confirm.submit' })); });

    const hint = document.getElementById('pay-amt-hint')!;
    expect(hint).toHaveTextContent('exceeds:{"amount":"5,00');
    expect(hint).toHaveTextContent('inTransit');
    expect(addToast).not.toHaveBeenCalledWith('error', 'paymentError');
    expect(props.onFundsChanged).toHaveBeenCalled();
  });
});
