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
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100vh',
          background: 'var(--color-bg)',
          color: 'var(--color-text-2)',
          fontFamily: 'var(--font-body)',
        }}
      >
        {t('loading')}
      </div>
    );
  }

  return <>{children}</>;
}
