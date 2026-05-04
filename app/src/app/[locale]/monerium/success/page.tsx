'use client';
import { useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { MoneriumPopupMessage } from '@/types/monerium';

export default function MoneriumSuccessPage() {
  const t = useTranslations('dashboard.moneriumPopup.success');

  useEffect(() => {
    window.opener?.postMessage({ type: MoneriumPopupMessage.CONNECTED }, window.location.origin);
    setTimeout(() => window.close(), 500);
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <div className="card card-no-hover text-center p-8">
        <div className="text-4xl mb-4">✅</div>
        <p className="font-display font-bold text-lg text-green">{t('title')}</p>
        <p className="mt-2 text-sm text-text-2">{t('closing')}</p>
      </div>
    </div>
  );
}
