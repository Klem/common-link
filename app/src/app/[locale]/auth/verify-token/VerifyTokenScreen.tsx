'use client';

import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useMagicLinkVerify } from '@/hooks/auth/useMagicLinkVerify';
import { useAuthStore } from '@/stores/authStore';
import { ROUTES } from '@/lib/routes';
import { useRouter } from 'next/navigation';
import { AuthCard } from '@/components/auth/AuthCard';

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
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <AuthCard className="max-w-[480px] w-full text-center">
        {status !== 'error' && (
          <>
            <div className="mb-6 flex justify-center">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-teal)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" opacity="0.3" />
                <path d="M12 6v6l4 2" />
              </svg>
            </div>
            <div className="flex items-center gap-2 justify-center text-text-2">
              <span className="animate-spin-around w-4 h-4 border-2 border-current border-t-transparent rounded-full" />
              <p>{t('verifyEmail.verifying')}</p>
            </div>
          </>
        )}

        {status === 'error' && error && (
          <>
            <div className="mb-6 alert alert-error">
              <span>{t(error as Parameters<typeof t>[0])}</span>
            </div>
            <h1 className="mb-3 font-display font-black text-2xl text-text">
              {t('verifyEmail.failed')}
            </h1>
            <Link
              href={`/${locale}${ROUTES.CHECK_EMAIL}`}
              className="btn btn-primary btn-md w-full mt-4"
            >
              {t('verifyEmail.requestNew')}
            </Link>
          </>
        )}
      </AuthCard>
    </div>
  );
}
