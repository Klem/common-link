'use client';

import { useEffect, useState } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { useTranslations } from 'next-intl';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [hydrated, setHydrated] = useState(false);
  const t = useTranslations('common');

  useEffect(() => {
    useAuthStore.getState().hydrateFromStorage().finally(() => {
      setHydrated(true);
    });
  }, []);

  if (!hydrated) {
    return (
      <div className="flex items-center justify-center h-screen bg-bg text-text-2 font-body">
        {t('loading')}
      </div>
    );
  }

  return <>{children}</>;
}
