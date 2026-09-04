'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { getWidget, getDonationStatus, DonationReturnStatus } from '@/lib/api/public';
import { pushDonationEvent } from '@/lib/gtm';

/**
 * Worst-case Mollie webhook delivery lag, per `PublicWidgetService.getDonationStatus` — the
 * confirmation poll below must cover it, or a real confirmation lands after this page has already
 * redirected away and the GA4 `purchase` event is lost for good.
 */
const CONFIRMATION_POLL_TIMEOUT_MS = 30000;

/**
 * Poll spacing — `getDonationStatus` does a live Mollie status check on every call, so this also
 * bounds how many times the poll hits Mollie's API while waiting (~30 calls over the timeout above).
 */
const STATUS_POLL_INTERVAL_MS = 1000;

/**
 * Delay after the donation outcome is settled (confirmed, or the poll above gave up) before
 * auto-redirecting back to the association's site — gives the donor a moment to read the message.
 */
const POST_RESULT_REDIRECT_DELAY_MS = 5000;

/** Donation payload carried on the redirect URL, needed to push the `purchase` dataLayer event. */
export interface ReturnTrackingContext {
  ref: string;
  amount: number;
  currency: string;
  campaignId: string;
  campaignName: string;
  associationName: string;
  anonymous: boolean;
}

interface Props {
  widgetToken: string;
  locale: string;
  cancelled: boolean;
  source: string | null;
  tracking: ReturnTrackingContext | null;
}

export function EmbedDonateReturnClient({ widgetToken, locale, cancelled, source, tracking }: Props) {
  const t = useTranslations('widget.return');
  const [validatedSource, setValidatedSource] = useState<string | null>(null);

  // Whether the confirmation poll below has concluded — confirmed, or gave up after the timeout.
  // No tracking data at all (or an already-cancelled payment) means there is nothing to poll for,
  // so it starts settled: the redirect-back effect further down must not wait on it forever.
  const [pollSettled, setPollSettled] = useState(() => !tracking || cancelled);

  // Independent of the redirect-to-source effect below: a donation made directly on the landing
  // page (no widgetAllowedOrigin configured) never sets `source`, but must still be tracked.
  useEffect(() => {
    if (cancelled || !tracking) return;
    let stopped = false;
    const maxAttempts = Math.ceil(CONFIRMATION_POLL_TIMEOUT_MS / STATUS_POLL_INTERVAL_MS);

    const poll = async () => {
      for (let attempt = 0; attempt < maxAttempts && !stopped; attempt++) {
        try {
          const status = await getDonationStatus(tracking.ref);
          if (status.status === DonationReturnStatus.CONFIRMED) {
            pushDonationEvent(
              'purchase',
              {
                transaction_id: tracking.ref,
                value: tracking.amount,
                currency: tracking.currency,
                items: [{ item_id: tracking.campaignId, item_name: tracking.campaignName }],
                affiliation: tracking.associationName,
              },
              { anonymous: tracking.anonymous, paymentMethod: status.method },
            );
            if (!stopped) setPollSettled(true);
            return;
          }
        } catch {
          // Network hiccup — retried on the next tick, still bounded by maxAttempts.
        }
        if (!stopped && attempt < maxAttempts - 1) {
          await new Promise((resolve) => setTimeout(resolve, STATUS_POLL_INTERVAL_MS));
        }
      }
      // Confirmation did not land within the window: no push — pushing on a guess would be a false
      // conversion. The webhook may still confirm the donation later (receipt / on-chain enqueue
      // happen server-side regardless of this poll), it just never becomes a dataLayer `purchase`.
      if (!stopped) setPollSettled(true);
    };

    poll();
    return () => { stopped = true; };
  }, [cancelled, tracking]);

  useEffect(() => {
    if (!source) return;

    const run = async () => {
      try {
        const widget = await getWidget(widgetToken);
        if (!widget.widgetAllowedOrigin) {
          console.warn(
            `[CommonLink widget/return] redirect blocked: this association has no widgetAllowedOrigin configured. Set it to the exact origin (scheme+host+port) of the embedding page.`,
          );
          return;
        }

        let sourceOrigin: string;
        try {
          sourceOrigin = new URL(source).origin;
        } catch {
          console.warn(`[CommonLink widget/return] redirect blocked: source is not a valid URL:`, source);
          return;
        }

        // Compare by origin, tolerating a trailing slash on the stored allowlist value
        // (URL.origin never carries one; a manually-set widgetAllowedOrigin might).
        const allowedOrigin = widget.widgetAllowedOrigin.replace(/\/+$/, '');
        if (sourceOrigin !== allowedOrigin) {
          console.warn(
            `[CommonLink widget/return] redirect blocked: source origin "${sourceOrigin}" !== widgetAllowedOrigin "${allowedOrigin}". They must match exactly (scheme + host + port).`,
          );
          return;
        }

        setValidatedSource(source);

        if (cancelled) {
          const top = typeof window !== 'undefined' ? (window.top ?? window) : null;
          if (top) top.location.href = source;
        }
        // Non-cancelled: the redirect is scheduled by the effect below, once the confirmation poll
        // has settled — never on a fixed timer that could fire before a real (up to 30s late)
        // webhook confirmation.
      } catch (e) {
        // getWidget failed (network/CORS/404) — can't validate, no redirect
        console.warn(`[CommonLink widget/return] redirect blocked: could not fetch widget config for validation.`, e);
      }
    };

    run();
  }, [widgetToken, source, cancelled]);

  // Redirects back to the validated source once the donation outcome is known — always after the
  // confirmation poll above has settled, so it never cuts off a merely-late confirmation.
  useEffect(() => {
    if (cancelled || !validatedSource || !pollSettled) return;
    const top = typeof window !== 'undefined' ? (window.top ?? window) : null;
    if (!top) return;
    const timer = setTimeout(() => { top.location.href = validatedSource; }, POST_RESULT_REDIRECT_DELAY_MS);
    return () => clearTimeout(timer);
  }, [cancelled, validatedSource, pollSettled]);

  const fallbackUrl = `/${locale}/embed/donate/${widgetToken}`;

  if (cancelled) {
    return (
      <div className="widget-return">
        <div className="widget-return-cancelled" aria-live="polite">
          <h2>{t('cancelled.title')}</h2>
          <p>{t('cancelled.message')}</p>
          <a href={validatedSource ?? fallbackUrl} className="btn btn-secondary btn-md">
            {t('back')}
          </a>
        </div>
      </div>
    );
  }

  if (validatedSource) {
    return (
      <div className="widget-return">
        <div className="widget-return-success" aria-live="polite">
          <h2>{t('confirmed.title')}</h2>
          <p>{t('confirmed.message')}</p>
          <p style={{ fontSize: 13, color: 'var(--color-text-2)', marginTop: 8 }}>
            {t('redirecting')}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="widget-return">
      <div className="widget-return-loading" aria-live="polite">
        <p>{t('loading')}</p>
      </div>
    </div>
  );
}
