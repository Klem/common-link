'use client';

import { useTranslations } from 'next-intl';

interface DividerProps {
  text?: string;
}

export function Divider({ text }: DividerProps) {
  const t = useTranslations('auth');

  return (
    <div className="auth-divider">
      <span className="auth-divider-text">{text ?? t('divider.or')}</span>
    </div>
  );
}
