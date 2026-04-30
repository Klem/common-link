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
    <div className="role-toggle">
      {ROLES.map(({ key, emoji, labelKey }) => {
        const isActive = value === key;
        return (
          <button
            key={key}
            type="button"
            onClick={() => onChange(key)}
            className={`role-option${isActive ? ' active' : ''}`}
          >
            <span className="role-dot" />
            <span>{emoji}</span>
            <span>{t(labelKey)}</span>
          </button>
        );
      })}
    </div>
  );
}
