import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';

const donationFormMock = vi.fn((_props: unknown) => null);

vi.mock('next-intl', () => ({ useTranslations: () => (key: string) => key }));
vi.mock('@/components/donation/DonationForm', () => ({
  DonationForm: (props: unknown) => donationFormMock(props),
}));

import { DonationPanel } from '../DonationPanel';
import { PaymentProviderId } from '@/types/paymentProvider';

const tracking = { campaignId: 'c1', campaignName: 'Camp', associationName: 'Assoc', currency: 'EUR' };

describe('DonationPanel', () => {
  it('forwards parentOrigin to the donation form so begin_checkout reaches the host page', () => {
    render(
      <DonationPanel
        widgetToken="clk_abc"
        sourceSite={null}
        locale="fr"
        tracking={tracking}
        parentOrigin="https://host.example"
      />,
    );
    expect(donationFormMock).toHaveBeenCalledWith(
      expect.objectContaining({ parentOrigin: 'https://host.example' }),
    );
  });

  it('forwards collectUnavailable to the donation form so it shows "temporarily unavailable"', () => {
    render(
      <DonationPanel widgetToken="clk_abc" sourceSite={null} locale="fr" tracking={tracking} disabled collectUnavailable />,
    );
    expect(donationFormMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ disabled: true, collectUnavailable: true }),
    );
  });

  it('keeps the card pills and "secured" line for a Mollie association', () => {
    const { container } = render(
      <DonationPanel widgetToken="clk_abc" sourceSite={null} locale="fr" tracking={tracking} paymentProvider={PaymentProviderId.MOLLIE} />,
    );
    expect(container.querySelector('.lp-payment-methods')).not.toBeNull();
    expect(container.querySelector('.lp-donation-secured')).not.toBeNull();
  });

  it('forwards HelloAsso and drops the pills its official button already shows', () => {
    const { container } = render(
      <DonationPanel widgetToken="clk_abc" sourceSite={null} locale="fr" tracking={tracking} paymentProvider={PaymentProviderId.HELLOASSO} />,
    );
    expect(donationFormMock).toHaveBeenLastCalledWith(expect.objectContaining({ paymentProvider: PaymentProviderId.HELLOASSO }));
    expect(container.querySelector('.lp-payment-methods')).toBeNull();
    expect(container.querySelector('.lp-donation-secured')).toBeNull();
  });
});
