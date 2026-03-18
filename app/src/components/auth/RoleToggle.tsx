'use client';

import { useTranslations } from 'next-intl';

type UserRole = 'ASSOCIATION' | 'DONOR';

interface RoleToggleProps {
  value: UserRole;
  onChange: (role: UserRole) => void;
}

const ROLES: Array<{ key: UserRole; emoji: string; labelKey: 'roles.association' | 'roles.donor' }> = [
  { key: 'ASSOCIATION', emoji: '🏛️', labelKey: 'roles.association' },
  { key: 'DONOR', emoji: '🤝', labelKey: 'roles.donor' },
];

export function RoleToggle({ value, onChange }: RoleToggleProps) {
  const t = useTranslations('auth');

  return (
    <div className="grid grid-cols-2 bg-bg-3 rounded-[10px] p-[4px] gap-[4px] mb-5">
      {ROLES.map(({ key, emoji, labelKey }) => {
        const isActive = value === key;
        return (
          <button
            key={key}
            type="button"
            onClick={() => onChange(key)}
            className={`py-[10px] px-2 rounded-[8px] border-none font-body text-[13px] font-medium cursor-pointer transition-all duration-[250ms] flex items-center justify-center gap-[7px] ${
              isActive
                ? 'bg-bg-2 text-text shadow-[0_2px_12px_rgba(0,0,0,.3)]'
                : 'bg-transparent text-text-2'
            }`}
          >
            <span
              className={`w-[6px] h-[6px] rounded-full flex-shrink-0 transition-all duration-[250ms] ${
                isActive
                  ? 'bg-green shadow-[0_0_8px_var(--color-green-glow)]'
                  : 'bg-muted shadow-none'
              }`}
            />
            <span>{emoji}</span>
            <span>{t(labelKey)}</span>
          </button>
        );
      })}
    </div>
  );
}
