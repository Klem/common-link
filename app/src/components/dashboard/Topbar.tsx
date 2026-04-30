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
  subtitle?: string;
  onMenuToggle?: () => void;
}

export function Topbar({ title, subtitle, onMenuToggle }: TopbarProps) {
  const user = useAuthStore((s) => s.user);
  const contextToggle = useContext(SidebarToggleContext);
  const handleBurger = onMenuToggle ?? contextToggle;

  const initials = user ? getInitials(user.displayName, user.email) : '';

  return (
    <div className="app-topbar">
      {/* Left: burger + title */}
      <div className="app-topbar-left">
        <button
          type="button"
          className="app-topbar-burger"
          onClick={handleBurger}
          aria-label="Ouvrir le menu"
        >
          <svg width="18" height="14" viewBox="0 0 18 14" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <rect width="18" height="2" rx="1" fill="currentColor" />
            <rect y="6" width="18" height="2" rx="1" fill="currentColor" />
            <rect y="12" width="18" height="2" rx="1" fill="currentColor" />
          </svg>
        </button>
        {(title || subtitle) && (
          <div className="app-topbar-heading">
            {title && <h1 className="app-topbar-title">{title}</h1>}
            {subtitle && <p className="app-topbar-subtitle">{subtitle}</p>}
          </div>
        )}
      </div>

      {/* Right: notification + avatar */}
      <div className="app-topbar-right">
        <button
          type="button"
          className="w-[32px] h-[32px] rounded-[8px] border border-border bg-transparent flex items-center justify-center text-[15px] text-text-2 hover:border-cyan hover:text-cyan transition-colors duration-150"
          aria-label="Notifications"
        >
          🔔
        </button>
        <div className="w-[32px] h-[32px] rounded-full bg-gradient-to-br from-green to-cyan flex items-center justify-center font-display font-extrabold text-[12px] text-black flex-shrink-0">
          {initials}
        </div>
      </div>
    </div>
  );
}
