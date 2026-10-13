'use client';
import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { MolliePopupMessage } from '@/types/mollie-connect';

/** Seconds the failure stays on screen before the popup closes itself (long enough to be read). */
const CLOSE_DELAY_SECONDS = 5;

/** `reason` values the backend may add to the error redirect (see `MollieConnectWebhookController`). */
const ErrorReason = {
  ORGANIZATION_ALREADY_LINKED: 'organization-already-linked',
} as const;

/**
 * Landing page of the Mollie OAuth popup after a failed connection. Tells the opener, explains the
 * failure (the generic message, or the specific one for a known `reason`), then closes the popup
 * after a visible countdown. There is deliberately no close button: the opener keeps the
 * connection modal until this window is gone, so the user always reads the explanation.
 */
export default function MollieConnectErrorPage() {
  const t = useTranslations('dashboard.molliePopup.error');
  const [secondsLeft, setSecondsLeft] = useState(CLOSE_DELAY_SECONDS);
  const [alreadyLinked, setAlreadyLinked] = useState(false);

  useEffect(() => {
    setAlreadyLinked(
      new URLSearchParams(window.location.search).get('reason') === ErrorReason.ORGANIZATION_ALREADY_LINKED,
    );
    window.opener?.postMessage({ type: MolliePopupMessage.ERROR }, window.location.origin);
  }, []);

  useEffect(() => {
    if (secondsLeft <= 0) {
      window.close();
      return;
    }
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [secondsLeft]);

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <div className="card card-no-hover text-center p-8" role="alert">
        <div className="text-4xl mb-4">⚠️</div>
        <p className="font-display font-bold text-lg text-coral">{t('title')}</p>
        <p className="mt-2 text-sm text-text-2">
          {alreadyLinked ? t('organizationAlreadyLinked') : t('retry')}
        </p>
        <p className="mt-4 text-xs text-text-2" aria-live="off">{t('closing', { seconds: Math.max(secondsLeft, 0) })}</p>
      </div>
    </div>
  );
}
