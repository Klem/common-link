'use client';

import { useEffect } from 'react';
import { getDonationStatus, DonationReturnStatus } from '@/lib/api/public';
import { DonationEventName, pushDonationEvent, relayDonationEvent } from '@/lib/gtm';

/**
 * The donor reaches the campaign page only after `/return` saw the payment confirmed, so the
 * status is normally CONFIRMED on the first read; the few retries only absorb a network hiccup.
 */
const MAX_ATTEMPTS = 5;
const RETRY_DELAY_MS = 1000;

/** Same key prefix as the host loaders: a ref already sent in this tab is never sent again. */
const PURCHASE_DONE_PREFIX = 'cl-purchase-';

/** Same names as `DONATION_RESULT_PARAM` / `DONATION_REF_PARAM` on `/return`. */
const RETURN_PARAMS = ['cl_donation', 'cl_ref'];

interface Props {
  /** Public ref of the confirmed donation. */
  donationRef: string;
  /**
   * Validated origin of the association's page when this runs in its CommonLink iframe: the event
   * is then relayed to that page only. Absent on the landing page opened directly: the event is
   * pushed into this page's own dataLayer, where `begin_checkout` fired.
   */
  parentOrigin?: string | null;
}

function alreadySent(donationRef: string): boolean {
  try {
    return window.sessionStorage.getItem(PURCHASE_DONE_PREFIX + donationRef) !== null;
  } catch {
    return false;
  }
}

function markSent(donationRef: string): void {
  try {
    window.sessionStorage.setItem(PURCHASE_DONE_PREFIX + donationRef, '1');
  } catch {
    // storage unavailable — the address bar is cleaned anyway, a plain reload stays safe
  }
}

/** Removes the success flag from the address bar so a reload or a shared link never replays it. */
function stripReturnParams(): void {
  const url = new URL(window.location.href);
  if (!RETURN_PARAMS.some((name) => url.searchParams.has(name))) return;
  RETURN_PARAMS.forEach((name) => url.searchParams.delete(name));
  window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
}

/**
 * Sends the GA4 `purchase` event once the donor is back on the campaign page.
 *
 * Every value is read from the server (`getDonationStatus`), never from a URL, so it cannot be
 * tampered with. Two modes:
 * - inside the association's page (CommonLink iframe, `parentOrigin` set): relayed only, never
 *   pushed into this iframe's own dataLayer, so it runs once, under the association's GTM and
 *   consent. The host loader cleans the URL and prevents replays.
 * - on the landing page opened directly: pushed into this page's own dataLayer (its GTM and cookie
 *   banner); this component then cleans the URL and prevents replays itself.
 *
 * Renders nothing.
 */
export function PurchaseRelay({ donationRef, parentOrigin }: Props) {
  useEffect(() => {
    let stopped = false;
    const local = !parentOrigin;
    if (local) {
      stripReturnParams();
      if (alreadySent(donationRef)) return;
    }

    const send = async () => {
      for (let attempt = 0; attempt < MAX_ATTEMPTS && !stopped; attempt++) {
        try {
          const status = await getDonationStatus(donationRef);
          if (status.status === DonationReturnStatus.CONFIRMED) {
            const { amount, currency, campaignId, campaignName, associationName } = status;
            if (stopped || amount == null || !currency || !campaignId || !campaignName || !associationName) return;
            const ecommerce = {
              transaction_id: donationRef,
              value: amount,
              currency,
              items: [{ item_id: campaignId, item_name: campaignName }],
              affiliation: associationName,
            };
            const extras = { anonymous: status.anonymous ?? false, paymentMethod: status.method };
            if (parentOrigin) {
              relayDonationEvent(DonationEventName.PURCHASE, ecommerce, extras, parentOrigin);
            } else if (!alreadySent(donationRef)) {
              pushDonationEvent(DonationEventName.PURCHASE, ecommerce, extras);
              markSent(donationRef);
            }
            return;
          }
        } catch {
          // Network hiccup — retried below, bounded by MAX_ATTEMPTS.
        }
        if (!stopped && attempt < MAX_ATTEMPTS - 1) {
          await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
        }
      }
      // Still not confirmed: nothing is sent — a `purchase` on a guess would be a false conversion.
    };

    send();
    return () => { stopped = true; };
  }, [donationRef, parentOrigin]);

  return null;
}
