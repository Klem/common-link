'use client';

import { useTranslations } from 'next-intl';

type View = 'login' | 'signup';

interface ViewTabsProps {
  value: View;
  onChange: (view: View) => void;
}

export function ViewTabs({ value, onChange }: ViewTabsProps) {
  const t = useTranslations('auth');

  return (
    <div className="view-tabs">
      {(['login', 'signup'] as const).map((tab) => (
        <button
          key={tab}
          type="button"
          onClick={() => onChange(tab)}
          className={`view-tab${value === tab ? ' active' : ''}`}
        >
          {t(`tabs.${tab}`)}
        </button>
      ))}
    </div>
  );
}
