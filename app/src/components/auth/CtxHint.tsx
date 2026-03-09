'use client';

import { useTranslations } from 'next-intl';

interface CtxHintProps {
  variant: 'donor' | 'association';
}

export function CtxHint({ variant }: CtxHintProps) {
  const t = useTranslations('auth');

  const borderColor = variant === 'donor' ? 'var(--color-yellow)' : 'var(--color-green)';

  return (
    <div
      className="text-[12px] text-text-2 mb-[18px] px-3 py-[9px] bg-bg-3 rounded-[8px] border-l-[3px] leading-relaxed transition-all duration-300"
      style={{ borderLeftColor: borderColor }}
    >
      {t(`hints.${variant}`)}
    </div>
  );
}
