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
    <div className="grid grid-cols-2 bg-bg-3 rounded-[11px] p-[3px] gap-[3px] mb-[22px]">
      {(['login', 'signup'] as const).map((tab) => (
        <button
          key={tab}
          type="button"
          onClick={() => onChange(tab)}
          className={`py-[10px] rounded-[9px] border-none font-body text-[14px] font-medium cursor-pointer transition-all duration-[250ms] ease-[cubic-bezier(0.22,1,0.36,1)] ${
            value === tab
              ? 'bg-bg-2 text-text shadow-[0_2px_12px_rgba(0,0,0,.3)]'
              : 'bg-transparent text-text-2'
          }`}
        >
          {t(`tabs.${tab}`)}
        </button>
      ))}
    </div>
  );
}
