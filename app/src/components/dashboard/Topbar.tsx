'use client';

import { useAuthStore } from '@/stores/authStore';

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
  title: string;
  subtitle?: string;
}

export function Topbar({ title, subtitle }: TopbarProps) {
  const user = useAuthStore((s) => s.user);

  const initials = user ? getInitials(user.displayName, user.email) : '';

  return (
    <div className="flex items-center justify-between mb-[34px]">
      {/* Left: title + subtitle */}
      <div>
        <h1 className="font-display text-[25px] font-bold text-text leading-tight">{title}</h1>
        {subtitle && (
          <p className="text-[12px] text-text-2 mt-[3px]">{subtitle}</p>
        )}
      </div>

      {/* Right: notification + avatar */}
      <div className="flex items-center gap-[10px]">
        {/* Notification placeholder */}
        <button
          type="button"
          className="w-[32px] h-[32px] rounded-[8px] border border-border bg-transparent flex items-center justify-center text-[15px] text-text-2 hover:border-cyan hover:text-cyan transition-colors duration-150"
          aria-label="Notifications"
        >
          🔔
        </button>

        {/* User avatar */}
        <div className="w-[32px] h-[32px] rounded-full bg-gradient-to-br from-green to-cyan flex items-center justify-center font-display font-extrabold text-[12px] text-black flex-shrink-0">
          {initials}
        </div>
      </div>
    </div>
  );
}
