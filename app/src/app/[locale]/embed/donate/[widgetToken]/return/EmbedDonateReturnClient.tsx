'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { getWidget } from '@/lib/api/public';

/** Delay before auto-redirecting to sourceSite after a successful donation. */
const SUCCESS_REDIRECT_DELAY_MS = 3000;

interface Props {
  widgetToken: string;
  locale: string;
  cancelled: boolean;
  source: string | null;
}

export function EmbedDonateReturnClient({ widgetToken, locale, cancelled, source }: Props) {
  const t = useTranslations('widget.return');
  const [validatedSource, setValidatedSource] = useState<string | null>(null);

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
