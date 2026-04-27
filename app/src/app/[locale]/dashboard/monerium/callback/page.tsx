'use client';
import { Suspense, useEffect } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { MoneriumPopupMessage } from '@/types/monerium';

function CallbackContent() {
  const t = useTranslations('moneriumPopup.callback');
  const searchParams = useSearchParams();

  useEffect(() => {
    const code = searchParams.get('code');
    const state = searchParams.get('state');

    if (!code || !state) {
      window.opener?.postMessage({ type: MoneriumPopupMessage.ERROR }, window.location.origin);
      window.close();
      return;
    }

    // By the time the popup lands here via backend redirect, the backend already
    // exchanged the code for tokens. This page only signals the opener and closes.
    window.opener?.postMessage({ type: MoneriumPopupMessage.CONNECTED }, window.location.origin);
    window.close();
  }, [searchParams]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-white">
      <p className="text-sm text-gray-500">{t('closing')}</p>
    </div>
  );
}

export default function MoneriumCallbackPage() {
  const t = useTranslations('moneriumPopup.callback');

  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center bg-white">
          <p className="text-sm text-gray-500">{t('loading')}</p>
        </div>
      }
    >
      <CallbackContent />
    </Suspense>
  );
}
