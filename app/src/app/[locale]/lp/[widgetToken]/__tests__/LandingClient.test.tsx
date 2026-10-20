import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';

const donationPanelMock = vi.fn((_props: unknown) => null);

vi.mock('../DonationPanel', () => ({
  DonationPanel: (props: unknown) => donationPanelMock(props),
}));
vi.mock('../StickyBar', () => ({ StickyBar: () => null }));

import { LandingClient } from '../LandingClient';

const tracking = { campaignId: 'c1', campaignName: 'Camp', associationName: 'Assoc', currency: 'EUR' };

const renderClient = (parentOrigin: string | null) =>
  render(
    <LandingClient
      widgetToken="clk_abc"
      sourceSite="https://asso.example/campagne"
      locale="fr"
      campaignName="Camp"
      tracking={tracking}
      parentOrigin={parentOrigin}
    >
      <div />
    </LandingClient>,
  );

const lastSourceSite = () =>
  (donationPanelMock.mock.calls.at(-1)?.[0] as { sourceSite: string | null }).sourceSite;

beforeEach(() => {
  donationPanelMock.mockClear();
  history.pushState({}, '', '/fr/lp/clk_abc?utm_source=ads&gclid=xyz');
});

describe('LandingClient post-payment return target', () => {
  it('opened directly: returns to this very page, without its query', async () => {
    renderClient(null);
    await waitFor(() => expect(lastSourceSite()).toBe(`${window.location.origin}/fr/lp/clk_abc`));
  });

  it('embedded through a CommonLink loader: returns to the association campaign page', async () => {
    renderClient('https://asso.example');
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(lastSourceSite()).toBe('https://asso.example/campagne');
  });
});
