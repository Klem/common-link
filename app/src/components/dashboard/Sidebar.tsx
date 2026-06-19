'use client';

import Link from 'next/link';
import { useTranslations, useLocale } from 'next-intl';
import type { UserDto } from '@/types/auth';
import { UserRole } from '@/types/auth';
import { ROUTES } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { useCampaignCountStore } from '@/stores/campaignCountStore';

interface NavItem {
  icon: string;
  labelKey: string;
  href: string;
  badge?: boolean;
}

interface NavGroup {
  labelKey: string;
  items: NavItem[];
}

const DONOR_NAV: NavItem[] = [
  { icon: '🏠', labelKey: 'nav.overview',  href: ROUTES.DONOR_DASHBOARD },
  { icon: '👤', labelKey: 'nav.profile',   href: ROUTES.DONOR_PROFILE },
  { icon: '🎁', labelKey: 'nav.donations', href: '#' },
  { icon: '🎯', labelKey: 'nav.campaigns', href: '#' },
];

const ASSOCIATION_NAV_GROUPS: NavGroup[] = [
  {
    labelKey: 'nav.group.principal',
    items: [
      { icon: '🏠', labelKey: 'nav.overview',  href: ROUTES.ASSOCIATION_DASHBOARD },
      { icon: '📢', labelKey: 'nav.campaigns', href: ROUTES.ASSOCIATION_CAMPAIGNS, badge: true },
      { icon: '📊', labelKey: 'nav.reporting', href: ROUTES.ASSOCIATION_REPORTING },
    ],
  },
  {
    labelKey: 'nav.group.gestion',
    items: [
      { icon: '👤', labelKey: 'nav.payees', href: ROUTES.ASSOCIATION_PAYEES },
    ],
  },
  {
    labelKey: 'nav.group.settings',
    items: [
      { icon: '⚙️', labelKey: 'nav.settings', href: ROUTES.ASSOCIATION_PROFILE },
    ],
  },
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

function isNavActive(href: string, currentPath: string): boolean {
  if (href === '#') return false;
  return currentPath === href;
}

export function Sidebar({ user, currentPath, isOpen = false }: SidebarProps) {
  const t = useTranslations('dashboard');
  const locale = useLocale();
  const logout = useAuthStore((s) => s.logout);
  const { done, total } = useAccStatusStore();
  const campaignCount = useCampaignCountStore((s) => s.count);

  const isAssociation = user.role === UserRole.ASSOCIATION;
  const initials = getInitials(user.displayName, user.email);

  const accPillClass = done === total ? 'ok' : done >= 1 ? 'partial' : 'blocked';
  const accPillLabel =
    done === total
      ? t('association.home.sidebar.accVerified' as Parameters<typeof t>[0])
      : done >= 1
        ? t('association.home.sidebar.accPartial' as Parameters<typeof t>[0], { done, total })
        : t('association.home.sidebar.accPending' as Parameters<typeof t>[0]);

  function renderItem(item: NavItem) {
    const active = isNavActive(item.href, currentPath);
    const inner = (
      <>
        <span className="icon">{item.icon}</span>
        {t(item.labelKey as Parameters<typeof t>[0])}
        {item.badge && campaignCount > 0 && (
          <span className="badge-count">{campaignCount}</span>
        )}
      </>
    );

    if (item.href === '#') {
      return (
        <li key={item.labelKey}>
          <button type="button" disabled>{inner}</button>
        </li>
      );
    }

    return (
      <li key={item.labelKey}>
        <Link href={`/${locale}${item.href}`} className={active ? 'active' : undefined}>
          {inner}
        </Link>
      </li>
    );
  }

  return (
    <aside className={`sidebar${isOpen ? ' open' : ''}`}>
      <div className="sidebar-head">
        <svg viewBox="0 0 28 28" fill="none" aria-hidden="true">
          <path d="M8 10C8 7.79 9.79 6 12 6h4c2.21 0 4 1.79 4 4v0c0-2.21-1.79 4-4 4h-4" stroke="#4ECDC4" strokeWidth="2.5" strokeLinecap="round" />
          <path d="M20 18c0 2.21-1.79 4-4 4h-4c-2.21 0-4-1.79-4-4v0c0-2.21 1.79-4 4-4h4" stroke="rgba(255,255,255,.5)" strokeWidth="2.5" strokeLinecap="round" />
        </svg>
        <span className="sidebar-wordmark">Common<span>Link</span></span>
      </div>

      <div className="sidebar-org">
        <div className="sidebar-org-avatar">{initials[0]}</div>
        <div className="sidebar-org-info">
          <div className="sidebar-org-name">{user.displayName || user.email}</div>
          <div className="sidebar-org-type">
            {isAssociation
              ? t('nav.orgType' as Parameters<typeof t>[0])
              : t('roles.donor' as Parameters<typeof t>[0])}
          </div>
          {isAssociation && (
            <button type="button" className={`acc-mini ${accPillClass}`} title={accPillLabel}>
              <span className="acc-mini-dot" />
              <span>{accPillLabel}</span>
            </button>
          )}
        </div>
      </div>

      <nav className="sidebar-nav">
        {isAssociation ? (
          ASSOCIATION_NAV_GROUPS.map((group) => (
            <div key={group.labelKey}>
              <div className="snav-label">
                {t(group.labelKey as Parameters<typeof t>[0])}
              </div>
              <ul>{group.items.map(renderItem)}</ul>
            </div>
          ))
        ) : (
          <ul>{DONOR_NAV.map(renderItem)}</ul>
        )}
      </nav>

      <div className="sidebar-foot">
        <button type="button" onClick={logout}>
          ↩ {t('nav.logout' as Parameters<typeof t>[0])}
        </button>
      </div>
    </aside>
  );
}
