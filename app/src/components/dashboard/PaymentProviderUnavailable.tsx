'use client';

import { useTranslations } from 'next-intl';

/**
 * Minimal placeholder of the "Encaissement des dons" settings tab for an association whose payment provider has no
 * onboarding journey in the app yet (HelloAsso). Replaced by the HelloAsso journey in sprint step S7-08.
 */
export function PaymentProviderUnavailable() {
  const t = useTranslations('dashboard.association.profile.providerUnavailable');

  return (
    <div className="cm-card" role="status">
      <div className="cm-card-title">{t('title')}</div>
      <p>{t('body')}</p>
    </div>
  );
}
