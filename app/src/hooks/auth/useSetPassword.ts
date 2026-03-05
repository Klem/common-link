'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale } from 'next-intl';
import api from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';

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
      setError('auth.errors.genericError');
    } finally {
      setLoading(false);
    }
  };

  const onSkip = () => {
    router.push(getDashboardPath());
  };

  return { onSubmit, onSkip, loading, error };
}
