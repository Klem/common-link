'use client';

import { useEffect, useState } from 'react';
import { usePathname } from 'next/navigation';
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
 * Embed routes (`/embed/`) and the public landing page (`/lp/`) are skipped — both are
 * guest-facing, read nothing from `useAuthStore`, and would otherwise show a "loading" screen
 * (and, server-side, no real content at all) while a refresh call that only matters for
 * association/admin sessions resolves.
 *
 * The guest-route check seeds `hydrated`'s *initial* state, not just a post-mount effect:
 * `usePathname()` resolves during SSR too, so these routes render their real children in the
 * server-rendered HTML immediately — a `useEffect`-only skip would still leave the SSR output
 * stuck on the loading fallback, since effects never run server-side.
 *
 * This component must be placed above any component that reads from `useAuthStore`.
 *
 * @param children - The application subtree to render after hydration.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isGuestRoute = pathname.includes('/embed/') || pathname.includes('/lp/');
  const [hydrated, setHydrated] = useState(isGuestRoute);
  const t = useTranslations('common');

  useEffect(() => {
    if (isGuestRoute) {
      useAuthStore.setState({ isLoading: false });
      setHydrated(true);
      return;
    }
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
