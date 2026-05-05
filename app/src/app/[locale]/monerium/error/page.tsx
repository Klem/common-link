'use client';
import { useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { MoneriumPopupMessage } from '@/types/monerium';

export default function MoneriumErrorPage() {
  const t = useTranslations('dashboard.moneriumPopup.error');

  useEffect(() => {
    window.opener?.postMessage({ type: MoneriumPopupMessage.ERROR }, window.location.origin);
    setTimeout(() => window.close(), 1500);
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <div className="card card-no-hover text-center p-8">
        <div className="text-4xl mb-4">⚠️</div>
        <p className="font-display font-bold text-lg text-coral">{t('title')}</p>
        <p className="mt-2 text-sm text-text-2">{t('retry')}</p>
      </div>
    </div>
  );
}
