'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useMagicLinkVerify } from '@/hooks/auth/useMagicLinkVerify';
import { useAuthStore } from '@/stores/authStore';
import { ROUTES } from '@/lib/routes';
import { useRouter } from 'next/navigation';

interface VerifyTokenScreenProps {
  token: string;
}

export function VerifyTokenScreen({ token }: VerifyTokenScreenProps) {
  const t = useTranslations('auth');
  const locale = useLocale();
  const router = useRouter();

  const { status, error } = useMagicLinkVerify(token, () => {
    const user = useAuthStore.getState().user;
    const role = user?.role ?? 'DONOR';
    router.replace(`/${locale}/dashboard/${role.toLowerCase()}`);
  });

  return (
    <div className="min-h-screen flex items-center justify-center bg-bg px-4">
      <div
        className="w-full max-w-md rounded-xl border border-border bg-bg-2 text-center"
        style={{ padding: 'var(--space-card-p)' }}
      >
        {status !== 'error' && (
          <>
            <div className="mb-6 text-5xl">⏳</div>
            <p className="text-text-2">{t('verifyEmail.verifying')}</p>
          </>
        )}

        {status === 'error' && error && (
          <>
            <div className="mb-6 text-5xl">❌</div>
            <h1 className="mb-3 font-display text-2xl font-semibold text-red">
              {t('verifyEmail.failed')}
            </h1>
            <p className="mb-6 text-text-2">
              {t(error as Parameters<typeof t>[0])}
            </p>
            <Link
              href={`/${locale}${ROUTES.CHECK_EMAIL}`}
              className="inline-block rounded-md bg-green px-4 py-2 text-sm font-medium text-bg transition-opacity hover:opacity-90"
            >
              {t('verifyEmail.requestNew')}
            </Link>
          </>
        )}
      </div>
    </div>
  );
}
