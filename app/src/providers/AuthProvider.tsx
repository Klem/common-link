'use client';

import { useEffect, useState } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { useTranslations } from 'next-intl';

/**
 * Application-level authentication provider.
 *
 * Renders a full-screen loading indicator while `hydrateFromStorage` runs on
 * mount. This prevents protected routes from briefly rendering in an
 * unauthenticated state before the `cl-refresh` cookie has been exchanged for
 * a fresh access token.
 *
 * Once hydration completes (regardless of success or failure), `hydrated` is
 * set to `true` and children are rendered. The auth store's `isAuthenticated`
 * flag reflects whether hydration succeeded.
 *
 * This component must be placed above any component that reads from `useAuthStore`.
 *
 * @param children - The application subtree to render after hydration.
 */
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
