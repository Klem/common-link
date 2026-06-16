'use client';

import Link from 'next/link';
import { useTranslations, useLocale } from 'next-intl';
import type { UserDto } from '@/types/auth';
import { UserRole } from '@/types/auth';
import { ROUTES } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';
import { useAccStatusStore } from '@/stores/accStatusStore';

interface NavItem {
  icon: string;
  labelKey: string;
  href: string;
}

const DONOR_NAV: NavItem[] = [
  { icon: '🏠', labelKey: 'nav.overview',  href: ROUTES.DONOR_DASHBOARD },
  { icon: '👤', labelKey: 'nav.profile',   href: ROUTES.DONOR_PROFILE },
  { icon: '🎁', labelKey: 'nav.donations', href: '#' },
  { icon: '🎯', labelKey: 'nav.campaigns', href: '#' },
];

const ASSOCIATION_NAV: NavItem[] = [
  { icon: '🏠', labelKey: 'nav.overview',          href: ROUTES.ASSOCIATION_DASHBOARD },
  { icon: '📊', labelKey: 'nav.associationProfile', href: ROUTES.ASSOCIATION_PROFILE },
  { icon: '🏦', labelKey: 'nav.payees',             href: ROUTES.ASSOCIATION_PAYEES },
  { icon: '🎯', labelKey: 'nav.campaigns',          href: ROUTES.ASSOCIATION_CAMPAIGNS },
  { icon: '👥', labelKey: 'nav.donors',             href: '#' },
  { icon: '⚙️', labelKey: 'nav.settings',           href: '#' },
];

interface SidebarProps {
  user: UserDto;
  currentPath: string;
  isOpen?: boolean;
  onClose?: () => void;
}

function getInitials(displayName: string, email: string): string {
  const name = displayName?.trim();
  if (name) {
    const parts = name.split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }
    return parts[0][0].toUpperCase();
  }
  return email[0].toUpperCase();
}

function isNavItemActive(href: string, currentPath: string): boolean {
  if (href === '#') return false;
  return currentPath === href;
}

export function Sidebar({ user, currentPath, isOpen = false, onClose }: SidebarProps) {
  const t = useTranslations('dashboard');
  const locale = useLocale();
  const logout = useAuthStore((s) => s.logout);
  const { done, total } = useAccStatusStore();
  const navItems = user.role === UserRole.DONOR ? DONOR_NAV : ASSOCIATION_NAV;
  const initials = getInitials(user.displayName, user.email);

  const accPillClass = done === total ? 'ok' : done >= 1 ? 'partial' : 'blocked';
  const accPillLabel =
    done === total
      ? t('association.home.sidebar.accVerified' as Parameters<typeof t>[0])
      : done >= 1
        ? t('association.home.sidebar.accPartial' as Parameters<typeof t>[0], { done, total })
        : t('association.home.sidebar.accPending' as Parameters<typeof t>[0]);

  return (
    <nav className={`app-sidebar${isOpen ? ' open' : ''}`}>
      {/* Logo + close button (mobile) */}
      <div className="app-sidebar-header">
        <div className="flex items-center justify-between">
          <div className="app-sidebar-logo">
            <span className="w-[7px] h-[7px] bg-green rounded-full shadow-[0_0_8px_var(--color-green)] flex-shrink-0" />
            <span>Common</span>
            <span className="text-green -ml-1">Link</span>
          </div>
          {onClose && (
            <button
              type="button"
              onClick={onClose}
              aria-label="Fermer le menu"
              className="lg:hidden w-[28px] h-[28px] flex items-center justify-center rounded-[6px] text-white/50 hover:text-white hover:bg-white/10 transition-colors"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Nav items */}
      <ul className="app-nav">
        {navItems.map((item) => {
          const active = isNavItemActive(item.href, currentPath);

          if (item.href === '#') {
            return (
              <li key={item.labelKey}>
                <span className="app-nav-link disabled">
                  <span className="app-nav-icon">{item.icon}</span>
                  {t(item.labelKey as Parameters<typeof t>[0])}
                </span>
              </li>
            );
          }

          return (
            <li key={item.labelKey}>
              <Link
                href={`/${locale}${item.href}`}
                className={`app-nav-link${active ? ' active bg-green/10 text-green' : ''}`}
              >
                <span className="app-nav-icon">{item.icon}</span>
                {t(item.labelKey as Parameters<typeof t>[0])}
              </Link>
            </li>
          );
        })}
      </ul>

      {/* Bottom: user card + logout */}
      <div className="app-sidebar-user">
        <div className="app-sidebar-user-card">
          <div className="w-[44px] h-[44px] rounded-full bg-gradient-to-br from-green to-cyan flex items-center justify-center font-display font-extrabold text-[17px] text-black flex-shrink-0">
            {initials}
          </div>
          <div className="min-w-0 flex-1">
            <div className="font-display font-bold text-[13.5px] truncate">
              {user.displayName || user.email}
            </div>
            <span
              className={`inline-block mt-[3px] px-[9px] py-[2px] rounded-full text-[11px] font-semibold ${
                user.role === UserRole.DONOR
                  ? 'bg-green/[12%] text-green'
                  : 'bg-yellow/[12%] text-yellow'
              }`}
            >
              {user.role === UserRole.DONOR ? t('roles.donor') : t('roles.association')}
            </span>
            {user.role === UserRole.ASSOCIATION && (
              <button
                className={`acc-mini ${accPillClass}`}
                onClick={() => {}}
                title={accPillLabel}
              >
                <span className="acc-mini-dot" />
                <span>{accPillLabel}</span>
              </button>
            )}
          </div>
        </div>

        <button
          onClick={logout}
          className="app-nav-link hover:bg-red/10 hover:text-red"
        >
          <span className="app-nav-icon">↩</span>
          {t('nav.logout')}
        </button>
      </div>
    </nav>
  );
}
