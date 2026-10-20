import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';

const donationFormMock = vi.fn((_props: unknown) => null);

vi.mock('next-intl', () => ({ useTranslations: () => (key: string) => key }));
vi.mock('@/components/donation/DonationForm', () => ({
  DonationForm: (props: unknown) => donationFormMock(props),
}));

import { DonationPanel } from '../DonationPanel';

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
});
