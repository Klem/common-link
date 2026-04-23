'use client';

import Link from 'next/link';
import { useTranslations, useLocale } from 'next-intl';
import type { UserDto } from '@/types/auth';
import { UserRole } from '@/types/auth';
import { ROUTES } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';

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
  { icon: '🏦', labelKey: 'nav.payees',      href: ROUTES.ASSOCIATION_PAYEES },
  { icon: '🎯', labelKey: 'nav.campaigns',          href: ROUTES.ASSOCIATION_CAMPAIGNS },
  { icon: '👥', labelKey: 'nav.donors',             href: '#' },
  { icon: '⚙️', labelKey: 'nav.settings',           href: '#' },
];

interface SidebarProps {
  user: UserDto;
  currentPath: string;
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

export function Sidebar({ user, currentPath }: SidebarProps) {
  const t = useTranslations('dashboard');
  const locale = useLocale();
  const logout = useAuthStore((s) => s.logout);
  const navItems = user.role === UserRole.DONOR ? DONOR_NAV : ASSOCIATION_NAV;
  const initials = getInitials(user.displayName, user.email);

  return (
    <nav className="fixed left-0 top-0 h-screen w-[248px] bg-bg-2 border-r border-border flex flex-col overflow-y-auto z-40">
      {/* Logo */}
      <div className="px-[18px] pt-[24px] pb-[20px]">
        <div className="flex items-center gap-2 font-display font-extrabold text-[17px]">
          <span className="w-[7px] h-[7px] bg-green rounded-full shadow-[0_0_8px_var(--color-green)] flex-shrink-0" />
          <span className="text-text">Common</span>
          <span className="text-green -ml-1">Link</span>
        </div>
      </div>

      {/* Nav items */}
      <div className="flex flex-col gap-[2px] px-[10px] flex-1">
        {navItems.map((item) => {
          const active = isNavItemActive(item.href, currentPath);
          const baseClass =
            'flex items-center gap-[9px] px-[11px] py-[8px] rounded-[8px] text-[13px] transition-colors duration-150 w-full text-left';
          const stateClass = active
            ? 'bg-green/10 text-green'
            : 'text-text-2 hover:bg-bg-3 hover:text-text';

          if (item.href === '#') {
            return (
              <span
                key={item.labelKey}
                className={`${baseClass} ${stateClass} cursor-default opacity-50`}
              >
                <span className="text-[14px] w-[18px] text-center flex-shrink-0">{item.icon}</span>
                {t(item.labelKey as Parameters<typeof t>[0])}
              </span>
            );
          }

          return (
            <Link
              key={item.labelKey}
              href={`/${locale}${item.href}`}
              className={`${baseClass} ${stateClass}`}
            >
              <span className="text-[14px] w-[18px] text-center flex-shrink-0">{item.icon}</span>
              {t(item.labelKey as Parameters<typeof t>[0])}
            </Link>
          );
        })}
      </div>

      {/* Bottom user section */}
      <div className="px-[10px] pb-[20px] mt-auto flex flex-col gap-[6px]">
        <div className="flex items-center gap-[10px] px-[11px] py-[12px] rounded-[10px] bg-bg-3 border border-border">
          {/* Avatar */}
          <div className="w-[44px] h-[44px] rounded-full bg-gradient-to-br from-green to-cyan flex items-center justify-center font-display font-extrabold text-[17px] text-black flex-shrink-0">
            {initials}
          </div>

          {/* Name + role chip */}
          <div className="min-w-0 flex-1">
            <div className="font-display font-bold text-[13.5px] text-text truncate">
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
          </div>
        </div>

        <button
          onClick={logout}
          className="flex items-center gap-[9px] px-[11px] py-[8px] rounded-[8px] text-[13px] text-text-2 hover:bg-red/10 hover:text-red transition-colors duration-150 w-full text-left"
        >
          <span className="text-[14px] w-[18px] text-center flex-shrink-0">↩</span>
          {t('nav.logout')}
        </button>
      </div>
    </nav>
  );
}
