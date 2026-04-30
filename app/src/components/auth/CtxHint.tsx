'use client';

import { useTranslations } from 'next-intl';

interface CtxHintProps {
  variant: 'donor' | 'association';
}

export function CtxHint({ variant }: CtxHintProps) {
  const t = useTranslations('auth');

  const borderClass = variant === 'donor'
    ? '[border-left-color:var(--color-yellow)]'
    : '[border-left-color:var(--color-green)]';

  return (
    <div className={`ctx-hint ${borderClass}`}>
      {t(`hints.${variant}`)}
    </div>
  );
}
