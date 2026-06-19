'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useLocale } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { AuthProvider } from '@/types/auth';
import { ROUTES } from '@/lib/routes';
import { Sidebar } from './Sidebar';
import { SetPasswordModal } from './SetPasswordModal';
import { SidebarToggleContext } from './SidebarContext';

interface DashboardShellProps {
  children: React.ReactNode;
}

export function DashboardShell({ children }: DashboardShellProps) {
  const { user, isAuthenticated, isLoading } = useAuthStore();
  const router = useRouter();
  const locale = useLocale();
  const rawPathname = usePathname();
  const pathname = rawPathname.replace(new RegExp(`^/${locale}`), '') || '/';

  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace(`/${locale}${ROUTES.LOGIN}`);
    }
  }, [isLoading, isAuthenticated, router, locale]);

  useEffect(() => {
    if (!user) return;
    if (user.provider !== AuthProvider.MAGIC_LINK && user.provider !== AuthProvider.GOOGLE) return;
    const dismissedKey = `cl-password-modal-dismissed-${user.id}`;
    if (!localStorage.getItem(dismissedKey)) {
      setShowPasswordModal(true);
    }
  }, [user]);

  useEffect(() => {
    setSidebarOpen(false);
  }, [pathname]);

  const dismissModal = useCallback(() => {
    if (user) {
      localStorage.setItem(`cl-password-modal-dismissed-${user.id}`, '1');
    }
    setShowPasswordModal(false);
  }, [user]);

  const openSidebar = useCallback(() => setSidebarOpen(true), []);
  const closeSidebar = useCallback(() => setSidebarOpen(false), []);

  if (isLoading || !user) {
    return null;
  }

  return (
    <SidebarToggleContext.Provider value={openSidebar}>
      <div className="app-shell">
        <Sidebar user={user} currentPath={pathname} isOpen={sidebarOpen} onClose={closeSidebar} />
        {sidebarOpen && (
          <div className="sb-ov" onClick={closeSidebar} />
        )}
        <div className="main-area">
          {children}
        </div>
        <SetPasswordModal isOpen={showPasswordModal} onClose={dismissModal} />
      </div>
    </SidebarToggleContext.Provider>
  );
}
