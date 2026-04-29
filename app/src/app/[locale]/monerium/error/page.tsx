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
    <div className="flex min-h-screen items-center justify-center bg-white">
      <div className="text-center">
        <p className="text-2xl font-semibold text-red-500">{t('title')}</p>
        <p className="mt-2 text-sm text-gray-500">{t('retry')}</p>
      </div>
    </div>
  );
}
