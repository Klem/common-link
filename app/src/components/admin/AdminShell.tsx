'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { isCurator } from '@/types/auth';
import { ROUTES } from '@/lib/routes';
import Link from 'next/link';

interface AdminShellProps {
  children: React.ReactNode;
}

export function AdminShell({ children }: AdminShellProps) {
  const { user, isAuthenticated, isLoading } = useAuthStore();
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations('admin');
  const rawPathname = usePathname();
  const pathname = rawPathname.replace(new RegExp(`^/${locale}`), '') || '/';

  useEffect(() => {
    if (!isLoading && (!isAuthenticated || !isCurator(user))) {
      router.replace(`/${locale}${ROUTES.LOGIN}`);
    }
  }, [isLoading, isAuthenticated, user, router, locale]);

  if (isLoading || !user) {
    return null;
  }

  const handleLogout = () => {
    useAuthStore.getState().logout();
  };

  return (
    <div className="app-shell">
      <nav className="sidebar" style={{ borderTop: '3px solid var(--warm-coral)' }}>
        <div className="sidebar-head">
          <span className="sidebar-wordmark">
            common<span>link</span>
          </span>
        </div>

        <div className="sidebar-org">
          <div className="sidebar-org-avatar">⚙</div>
          <div className="sidebar-org-info">
            <div className="sidebar-org-name">{user.displayName || user.email}</div>
            <div className="sidebar-org-type">{t('shell.title')}</div>
          </div>
        </div>

        <div className="sidebar-nav">
          <p className="snav-label">Navigation</p>
          <ul>
            <li>
              <Link
                href={`/${locale}${ROUTES.admin.verifications}`}
                className={pathname.startsWith(ROUTES.admin.verifications) ? 'active' : ''}
              >
                <span className="icon">📋</span>
                {t('nav.verifications')}
              </Link>
            </li>
          </ul>
        </div>

        <div className="sidebar-nav" style={{ marginTop: 'auto', borderTop: '1px solid rgba(255,255,255,.08)', paddingTop: '12px' }}>
          <ul>
            <li>
              <button onClick={handleLogout}>
                <span className="icon">↩</span>
                {t('shell.logout')}
              </button>
            </li>
          </ul>
        </div>
      </nav>

      <div className="main-area">
        {children}
      </div>
    </div>
  );
}
