'use client';

import { useContext } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { SidebarToggleContext } from './SidebarContext';

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

interface TopbarProps {
  title?: string;
  parent?: string;
  onMenuToggle?: () => void;
}

export function Topbar({ title, parent, onMenuToggle }: TopbarProps) {
  const user = useAuthStore((s) => s.user);
  const contextToggle = useContext(SidebarToggleContext);
  const handleBurger = onMenuToggle ?? contextToggle;

  const initials = user ? getInitials(user.displayName, user.email) : '';
  const displayName = user?.displayName || user?.email || '';

  return (
    <div className="topbar">
      <div className="topbar-left">
        <button type="button" className="mob-toggle" onClick={handleBurger} aria-label="Ouvrir le menu">
          <i /><i /><i />
        </button>
        <div className="bc">
          {parent && (
            <>
              <button type="button">{parent}</button>
              <span className="sep">/</span>
            </>
          )}
          {title && <span className="current">{title}</span>}
        </div>
      </div>

      <div className="topbar-right">
        <button type="button" className="topbar-bell" aria-label="Notifications">
          🔔<span className="dot" />
        </button>
        <div className="topbar-user">
          <div className="topbar-user-av">{initials}</div>
          <span>{displayName}</span>
        </div>
      </div>
    </div>
  );
}
