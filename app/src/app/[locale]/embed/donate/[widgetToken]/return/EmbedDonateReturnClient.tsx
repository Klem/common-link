'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { getWidget, getDonationStatus, DonationReturnStatus } from '@/lib/api/public';
import { pushDonationEvent } from '@/lib/gtm';

/** Delay before auto-redirecting to sourceSite after a successful donation. */
const SUCCESS_REDIRECT_DELAY_MS = 5000;

/** Poll spacing for donation-status confirmation — bounded by SUCCESS_REDIRECT_DELAY_MS. */
const STATUS_POLL_INTERVAL_MS = 750;

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

  // Independent of the redirect-to-source effect below: a donation made directly on the landing
  // page (no widgetAllowedOrigin configured) never sets `source`, but must still be tracked.
  useEffect(() => {
    if (cancelled || !tracking) return;
    let stopped = false;
    const maxAttempts = Math.ceil(SUCCESS_REDIRECT_DELAY_MS / STATUS_POLL_INTERVAL_MS);

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
            return;
          }
        } catch {
          // Network hiccup — retried on the next tick, still bounded by maxAttempts.
        }
        if (!stopped && attempt < maxAttempts - 1) {
          await new Promise((resolve) => setTimeout(resolve, STATUS_POLL_INTERVAL_MS));
        }
      }
      // Confirmation did not land within the window: no push. The webhook may still confirm the
      // donation later, but by then this page has redirected away (or the donor closed the tab).
      //
      // Even a push that DOES happen here is best-effort, not guaranteed delivery: a late
      // confirmation (e.g. attempt 4, ~2.3s in) leaves GTM only a few hundred ms to load gtm.js
      // fresh on this page and fire the GA4 tag before the other effect's 5s timer navigates away.
      // A tag that hasn't finished evaluating when the page unloads never fires, dataLayer.push or
      // not — GA4's sendBeacon-on-unload only protects a tag that has already fired.
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

        const top = typeof window !== 'undefined' ? (window.top ?? window) : null;
        if (!top) return;

        if (cancelled) {
          top.location.href = source;
        } else {
          setTimeout(() => { top.location.href = source; }, SUCCESS_REDIRECT_DELAY_MS);
        }
      } catch (e) {
        // getWidget failed (network/CORS/404) — can't validate, no redirect
        console.warn(`[CommonLink widget/return] redirect blocked: could not fetch widget config for validation.`, e);
      }
    };

    run();
  }, [widgetToken, source, cancelled]);

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
