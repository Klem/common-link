'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale } from 'next-intl';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';

/**
 * Hook for the post-registration "set password" step.
 *
 * Called after a user has authenticated via magic link or Google and is prompted
 * to optionally set an email/password credential via `PATCH /api/user/me/password`.
 *
 * The dashboard path is derived from the current user's role in the auth store.
 * If `user` is null (edge case during hydration), it falls back to the donor dashboard.
 *
 * - `onSubmit(password)` — saves the password then redirects to the dashboard.
 * - `onSkip()` — skips password creation and redirects directly to the dashboard.
 *
 * @returns `onSubmit`, `onSkip`, `loading` flag, and i18n `error` key.
 */
export function useSetPassword() {
  const router = useRouter();
  const locale = useLocale();
  const { user } = useAuthStore();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const getDashboardPath = () =>
    `/${locale}/dashboard/${(user?.role ?? 'donor').toLowerCase()}`;

  const onSubmit = async (password: string): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      await api.patch('/api/user/me/password', { password });
      router.push(getDashboardPath());
    } catch {
      setError('errors.genericError');
    } finally {
      setLoading(false);
    }
  };

  const onSkip = () => {
    router.push(getDashboardPath());
  };

  return { onSubmit, onSkip, loading, error };
}
