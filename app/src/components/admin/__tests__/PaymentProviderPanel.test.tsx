import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PaymentProviderPanel } from '../PaymentProviderPanel';
import { changePaymentProvider, getPaymentProviderPreview } from '@/lib/api/admin';
import type { PaymentProviderPreview } from '@/types/admin';

vi.mock('next-intl', () => ({ useTranslations: () => (key: string) => key, useLocale: () => 'fr' }));
vi.mock('@/lib/api/admin', () => ({ getPaymentProviderPreview: vi.fn(), changePaymentProvider: vi.fn() }));

const mockedGet = vi.mocked(getPaymentProviderPreview);
const mockedChange = vi.mocked(changePaymentProvider);

const preview = (current: 'MOLLIE' | 'HELLOASSO'): PaymentProviderPreview => ({
  associationId: 'assoc-1',
  currentProvider: current,
  providers: [
    { provider: 'MOLLIE', readiness: 'READY' },
    { provider: 'HELLOASSO', readiness: null },
  ],
  liveCampaigns: 2,
  widgetEnabled: true,
  pendingDonations: 1,
});

async function renderLoaded() {
  render(<PaymentProviderPanel associationId="assoc-1" />);
  await waitFor(() => expect(screen.getByText('title')).toBeInTheDocument());
}

describe('PaymentProviderPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGet.mockResolvedValue(preview('MOLLIE'));
  });

  it('shows the current provider and its readiness', async () => {
    await renderLoaded();

    expect(screen.getByText('providers.MOLLIE')).toBeInTheDocument();
    expect(screen.getByText('readiness.READY')).toHaveClass('badge-success');
    // Only the other provider is offered as a target, labelled as not integrated yet.
    expect(screen.getByRole('option')).toHaveTextContent('providers.HELLOASSO — readiness.UNAVAILABLE');
  });

  it('refuses a blank reason without opening the confirmation nor calling the API', async () => {
    await renderLoaded();
    fireEvent.change(screen.getByLabelText('reason'), { target: { value: '   ' } });
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    expect(screen.getByText('reasonRequired')).toBeInTheDocument();
    expect(screen.queryByText('confirmTitle')).not.toBeInTheDocument();
    expect(mockedChange).not.toHaveBeenCalled();
  });

  it('refuses a reason over the maximum length (mirror of the backend)', async () => {
    await renderLoaded();
    fireEvent.change(screen.getByLabelText('reason'), { target: { value: 'x'.repeat(501) } });
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    expect(screen.getByText('reasonTooLong')).toBeInTheDocument();
    expect(mockedChange).not.toHaveBeenCalled();
  });

  it('changes the provider in two steps: confirmation with the impact, then the call with the trimmed reason', async () => {
    mockedChange.mockResolvedValue(preview('HELLOASSO'));
    await renderLoaded();

    fireEvent.change(screen.getByLabelText('reason'), { target: { value: '  asked by the board  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));

    expect(screen.getByText('confirmTitle')).toBeInTheDocument();
    expect(screen.getByText('confirmMessage')).toBeInTheDocument();
    expect(mockedChange).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(screen.getByText('success')).toBeInTheDocument());
    expect(mockedChange).toHaveBeenCalledWith('assoc-1', { provider: 'HELLOASSO', reason: 'asked by the board' });
    expect(screen.getByText('providers.HELLOASSO')).toBeInTheDocument();
  });

  it('shows invalidReason when the backend refuses the reason (422)', async () => {
    mockedChange.mockRejectedValue({ response: { status: 422 } });
    await renderLoaded();

    fireEvent.change(screen.getByLabelText('reason'), { target: { value: 'asked by the board' } });
    fireEvent.click(screen.getByRole('button', { name: 'submit' }));
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(screen.getByText('invalidReason')).toBeInTheDocument());
  });

  it('shows an error when the preview cannot be loaded', async () => {
    mockedGet.mockRejectedValue(new Error('down'));
    render(<PaymentProviderPanel associationId="assoc-1" />);

    await waitFor(() => expect(screen.getByText('error')).toBeInTheDocument());
  });
});
