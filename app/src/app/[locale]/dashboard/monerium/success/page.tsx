'use client';
import { useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { MoneriumPopupMessage } from '@/types/monerium';

export default function MoneriumSuccessPage() {
  const t = useTranslations('moneriumPopup.success');

  useEffect(() => {
    window.opener?.postMessage({ type: MoneriumPopupMessage.CONNECTED }, window.location.origin);
    setTimeout(() => window.close(), 500);
  }, []);

  return (
    <div className="flex min-h-screen items-center justify-center bg-white">
      <div className="text-center">
        <p className="text-2xl font-semibold text-green-600">{t('title')}</p>
        <p className="mt-2 text-sm text-gray-500">{t('closing')}</p>
      </div>
    </div>
  );
}
