import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';

vi.mock('@/lib/api/public', () => ({
  getDonationStatus: vi.fn(),
  DonationReturnStatus: { PENDING: 'PENDING', CONFIRMED: 'CONFIRMED' },
}));
vi.mock('@/lib/gtm', () => ({
  DonationEventName: { BEGIN_CHECKOUT: 'begin_checkout', PURCHASE: 'purchase' },
  relayDonationEvent: vi.fn(),
  pushDonationEvent: vi.fn(),
}));

import { getDonationStatus } from '@/lib/api/public';
import { pushDonationEvent, relayDonationEvent } from '@/lib/gtm';
import { PurchaseRelay } from '../PurchaseRelay';

const mockStatus = getDonationStatus as ReturnType<typeof vi.fn>;
const mockRelay = relayDonationEvent as ReturnType<typeof vi.fn>;
const mockPush = pushDonationEvent as ReturnType<typeof vi.fn>;

const REF = '8e8a8e77-2c48-4634-a306-9cb86f630de8';
const HOST = 'https://asso.example';

const confirmed = {
  status: 'CONFIRMED',
  method: 'creditcard',
  amount: 50,
  currency: 'EUR',
  campaignId: 'camp-1',
  campaignName: 'Soirée ZoFest',
  associationName: 'ZO PROD.',
  anonymous: false,
};

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  history.pushState({}, '', '/');
});

describe('PurchaseRelay', () => {
  it('relays purchase to the host with the server-side values', async () => {
    mockStatus.mockResolvedValue(confirmed);
    render(<PurchaseRelay donationRef={REF} parentOrigin={HOST} />);

    await waitFor(() => expect(mockRelay).toHaveBeenCalledTimes(1));
    expect(mockStatus).toHaveBeenCalledWith(REF);
    expect(mockRelay).toHaveBeenCalledWith(
      'purchase',
      {
        transaction_id: REF,
        value: 50,
        currency: 'EUR',
        items: [{ item_id: 'camp-1', item_name: 'Soirée ZoFest' }],
        affiliation: 'ZO PROD.',
      },
      { anonymous: false, paymentMethod: 'creditcard' },
      HOST,
    );
  });

  it('never relays a donation that is not confirmed', async () => {
    vi.useFakeTimers();
    try {
      mockStatus.mockResolvedValue({ status: 'PENDING' });
      render(<PurchaseRelay donationRef={REF} parentOrigin={HOST} />);
      await vi.advanceTimersByTimeAsync(10000);
      expect(mockRelay).not.toHaveBeenCalled();
      expect(mockStatus.mock.calls.length).toBeGreaterThan(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('never relays a confirmed status without its values', async () => {
    mockStatus.mockResolvedValue({ status: 'CONFIRMED', method: 'creditcard' });
    render(<PurchaseRelay donationRef={REF} parentOrigin={HOST} />);
    await waitFor(() => expect(mockStatus).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(mockRelay).not.toHaveBeenCalled();
  });
});

describe('PurchaseRelay on the landing page opened directly', () => {
  it('pushes purchase into its own dataLayer, cleans the URL and never replays it', async () => {
    mockStatus.mockResolvedValue(confirmed);
    history.pushState({}, '', `/fr/lp/clk_abc?utm_source=ads&cl_donation=success&cl_ref=${REF}`);
    const { unmount } = render(<PurchaseRelay donationRef={REF} />);

    await waitFor(() => expect(mockPush).toHaveBeenCalledTimes(1));
    expect(mockRelay).not.toHaveBeenCalled();
    expect(mockPush).toHaveBeenCalledWith(
      'purchase',
      expect.objectContaining({ transaction_id: REF, value: 50, currency: 'EUR' }),
      { anonymous: false, paymentMethod: 'creditcard' },
    );
    expect(window.location.search).toBe('?utm_source=ads');
    unmount();

    // Reload of the same tab: already sent.
    render(<PurchaseRelay donationRef={REF} />);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(mockPush).toHaveBeenCalledTimes(1);
  });
});
