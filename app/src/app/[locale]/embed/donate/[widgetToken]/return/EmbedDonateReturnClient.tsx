'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { getWidget, getDonationStatus, DonationReturnStatus } from '@/lib/api/public';

/**
 * Worst-case Mollie webhook delivery lag, per `PublicWidgetService.getDonationStatus`. The
 * confirmation poll below must cover it: the success flag on the redirect back is only added once
 * the server has confirmed the payment.
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

/**
 * Query parameters appended to the association's campaign page URL on the redirect back, once the
 * server has confirmed the payment: `cl_donation=success&cl_ref=<donation ref>`. The CommonLink
 * loader on that page (`widget.js`, `landing.js`, iframe bridge) reads them and has its iframe relay
 * the GA4 `purchase` event to the page's own GTM, with values read from the server — never from
 * these parameters. Must stay literal matches of the loaders' copies.
 */
export const DONATION_RESULT_PARAM = 'cl_donation';
export const DONATION_REF_PARAM = 'cl_ref';
export const DONATION_RESULT_SUCCESS = 'success';

/**
 * Returns [source] with the success flag and the donation [ref] added, or [source] unchanged when
 * the payment was not confirmed by the server (`ref` null) — never flags a guess.
 */
export function withDonationSuccessFlag(source: string, ref: string | null): string {
  if (!ref) return source;
  const url = new URL(source);
  url.searchParams.set(DONATION_RESULT_PARAM, DONATION_RESULT_SUCCESS);
  url.searchParams.set(DONATION_REF_PARAM, ref);
  return url.toString();
}

/** Origin of [url], or null when it is not an absolute URL. */
function originOf(url: string): string | null {
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

/**
 * Whether [source] is this widget's own landing page on this app's origin (`/{locale}/lp/{token}`):
 * the donation started on the landing page opened directly (see `LandingClient`), so the donor goes
 * back there. A same-origin page of this very widget — never an open redirect.
 */
function isOwnLandingPage(source: string, widgetToken: string): boolean {
  try {
    const url = new URL(source);
    const [locale, segment, token, ...rest] = url.pathname.split('/').filter(Boolean);
    return (
      url.origin === window.location.origin &&
      /^[a-z]{2}$/.test(locale ?? '') &&
      segment === 'lp' &&
      token === widgetToken &&
      rest.length === 0
    );
  } catch {
    return false;
  }
}

interface Props {
  widgetToken: string;
  locale: string;
  cancelled: boolean;
  /** Page the donor came from — the association's campaign page. */
  source: string | null;
  /** Opaque public ref of the donation (`ref` on the Mollie success redirect). */
  donationRef: string | null;
}

/**
 * Post-payment page, always top-level on CommonLink's own origin. It pushes no dataLayer event and
 * loads no GTM: the `purchase` event belongs to the association's own page and runs under its
 * consent (RGPD), so this page only waits for the server to confirm the payment, then sends the
 * donor back to that page with the success flag.
 */
export function EmbedDonateReturnClient({ widgetToken, locale, cancelled, source, donationRef }: Props) {
  const t = useTranslations('widget.return');
  const [validatedSource, setValidatedSource] = useState<string | null>(null);

  // Whether the confirmation poll below has concluded — confirmed, or gave up after the timeout.
  // No ref at all (or an already-cancelled payment) means there is nothing to poll for, so it
  // starts settled: the redirect-back effect further down must not wait on it forever.
  const [pollSettled, setPollSettled] = useState(() => !donationRef || cancelled);

  // Set only when the server reported CONFIRMED — drives the success flag on the redirect back.
  const [confirmedRef, setConfirmedRef] = useState<string | null>(null);

  useEffect(() => {
    if (cancelled || !donationRef) return;
    let stopped = false;
    const maxAttempts = Math.ceil(CONFIRMATION_POLL_TIMEOUT_MS / STATUS_POLL_INTERVAL_MS);

    const poll = async () => {
      for (let attempt = 0; attempt < maxAttempts && !stopped; attempt++) {
        try {
          const status = await getDonationStatus(donationRef);
          if (status.status === DonationReturnStatus.CONFIRMED) {
            if (!stopped) {
              setConfirmedRef(donationRef);
              setPollSettled(true);
            }
            return;
          }
        } catch {
          // Network hiccup — retried on the next tick, still bounded by maxAttempts.
        }
        if (!stopped && attempt < maxAttempts - 1) {
          await new Promise((resolve) => setTimeout(resolve, STATUS_POLL_INTERVAL_MS));
        }
      }
      // Confirmation did not land within the window: redirect without the flag rather than on a
      // guess. The webhook may still confirm the donation later (receipt / on-chain enqueue happen
      // server-side regardless), it just never becomes a `purchase` event.
      if (!stopped) setPollSettled(true);
    };

    poll();
    return () => { stopped = true; };
  }, [cancelled, donationRef]);

  useEffect(() => {
    if (!source) return;

    const redirectNowIfCancelled = (target: string) => {
      if (!cancelled) return;
      const top = typeof window !== 'undefined' ? (window.top ?? window) : null;
      if (top) top.location.href = target;
    };

    const run = async () => {
      if (isOwnLandingPage(source, widgetToken)) {
        setValidatedSource(source);
        redirectNowIfCancelled(source);
        return;
      }
      try {
        const widget = await getWidget(widgetToken);
        if (!widget.widgetAllowedOrigin) {
          console.warn(
            `[CommonLink widget/return] redirect blocked: this association has no campaign page URL configured (widgetAllowedOrigin).`,
          );
          return;
        }

        const sourceOrigin = originOf(source);
        if (!sourceOrigin) {
          console.warn(`[CommonLink widget/return] redirect blocked: source is not a valid URL:`, source);
          return;
        }

        // widgetAllowedOrigin holds the full campaign page URL (or, for values saved before V85, a
        // bare origin): only its origin gates the redirect.
        const allowedOrigin = originOf(widget.widgetAllowedOrigin);
        if (sourceOrigin !== allowedOrigin) {
          console.warn(
            `[CommonLink widget/return] redirect blocked: source origin "${sourceOrigin}" !== campaign page origin "${allowedOrigin}". They must match exactly (scheme + host + port).`,
          );
          return;
        }

        setValidatedSource(source);
        redirectNowIfCancelled(source);
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
    const target = withDonationSuccessFlag(validatedSource, confirmedRef);
    const timer = setTimeout(() => { top.location.href = target; }, POST_RESULT_REDIRECT_DELAY_MS);
    return () => clearTimeout(timer);
  }, [cancelled, validatedSource, pollSettled, confirmedRef]);

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
