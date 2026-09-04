'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { readStoredConsent, updateConsent, writeStoredConsent, type ConsentChoice } from '@/lib/consentMode';

interface Props {
  widgetToken: string;
  /** No gtmContainerId configured for this association → nothing to consent to, banner never shows. */
  gtmId: string | null;
}

/**
 * First-visit cookie consent banner for `/lp/[widgetToken]` — required by Google's EU User Consent
 * Policy and the CNIL whenever the association's own `gtmContainerId` is set.
 *
 * Only handles the first-visit decision. A choice made on a previous visit is already restored,
 * synchronously, by `consentBootstrapScript` before this component ever mounts (see
 * `lib/consentMode.ts`) — this component just needs to know whether to show itself.
 */
export function CookieConsentBanner({ widgetToken, gtmId }: Props) {
  const t = useTranslations('landing.cookieConsent');
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (!gtmId) return;
    setVisible(readStoredConsent(widgetToken) === null);
  }, [gtmId, widgetToken]);

  if (!gtmId || !visible) return null;

  function choose(choice: ConsentChoice) {
    updateConsent(choice);
    writeStoredConsent(widgetToken, choice);
    setVisible(false);
  }

  return (
    <div className="lp-cookie-consent" role="dialog" aria-live="polite">
      <p className="lp-cookie-consent-message">{t('message')}</p>
      <div className="lp-cookie-consent-actions">
        <button
          type="button"
          className="lp-cookie-consent-btn lp-cookie-consent-btn--refuse"
          onClick={() => choose('denied')}
        >
          {t('refuse')}
        </button>
        <button
          type="button"
          className="lp-cookie-consent-btn lp-cookie-consent-btn--accept"
          onClick={() => choose('granted')}
        >
          {t('accept')}
        </button>
      </div>
    </div>
  );
}
