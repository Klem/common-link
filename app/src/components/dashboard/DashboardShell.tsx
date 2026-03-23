'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useLocale } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { ROUTES } from '@/lib/routes';
import { Sidebar } from './Sidebar';

interface DashboardShellProps {
  children: React.ReactNode;
}

export function DashboardShell({ children }: DashboardShellProps) {
  const { user, isAuthenticated, isLoading } = useAuthStore();
  const router = useRouter();
  const locale = useLocale();
  // Strip the locale prefix so currentPath matches ROUTES constants (e.g. /dashboard/donor)
  const rawPathname = usePathname();
  const pathname = rawPathname.replace(new RegExp(`^/${locale}`), '') || '/';

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace(`/${locale}${ROUTES.LOGIN}`);
    }
  }, [isLoading, isAuthenticated, router, locale]);

  if (isLoading || !user) {
    return null;
  }

  return (
    <div className="flex min-h-screen bg-bg">
      <Sidebar user={user} currentPath={pathname} />
      <main className="ml-[248px] flex-1 px-[46px] py-[38px] min-h-screen">
        {children}
      </main>
    </div>
  );
}
