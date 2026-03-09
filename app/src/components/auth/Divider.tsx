'use client';

import { useTranslations } from 'next-intl';

interface DividerProps {
  text?: string;
}

export function Divider({ text }: DividerProps) {
  const t = useTranslations('auth');

  return (
    <div className="flex items-center gap-3 my-[18px]">
      <div className="flex-1 h-px bg-border" />
      <span className="text-[11px] text-muted font-medium whitespace-nowrap">
        {text ?? t('divider.or')}
      </span>
      <div className="flex-1 h-px bg-border" />
    </div>
  );
}
