'use client';
import { Suspense, useEffect } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { MoneriumPopupMessage } from '@/types/monerium';

function CallbackContent() {
  const t = useTranslations('dashboard.moneriumPopup.callback');
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
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <div className="card card-no-hover text-center p-8">
        <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
        <p className="text-sm text-text-2">{t('closing')}</p>
      </div>
    </div>
  );
}

export default function MoneriumCallbackPage() {
  const t = useTranslations('moneriumPopup.callback');

  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center px-4 py-12">
          <div className="card card-no-hover text-center p-8">
            <p className="text-sm text-text-2">{t('loading')}</p>
          </div>
        </div>
      }
    >
      <CallbackContent />
    </Suspense>
  );
}
