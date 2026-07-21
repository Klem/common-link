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
        if (!widget.widgetAllowedOrigin) return;

        let sourceOrigin: string;
        try {
          sourceOrigin = new URL(source).origin;
        } catch {
          return;
        }

        if (sourceOrigin !== widget.widgetAllowedOrigin) return;

        setValidatedSource(source);

        const top = typeof window !== 'undefined' ? (window.top ?? window) : null;
        if (!top) return;

        if (cancelled) {
          top.location.href = source;
        } else {
          setTimeout(() => { top.location.href = source; }, SUCCESS_REDIRECT_DELAY_MS);
        }
      } catch {
        // getWidget failed — can't validate, no redirect
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
