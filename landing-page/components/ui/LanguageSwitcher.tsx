'use client';

import { useLocale } from 'next-intl';
import { useRouter, usePathname } from '@/i18n/navigation';
import { Locale } from '@/i18n/config';

export function LanguageSwitcher() {
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();

  const switchLocale = (newLocale: Locale) => {
    router.replace(pathname, { locale: newLocale });
  };

  return (
    <div className="flex items-center gap-1">
      <button
        onClick={() => switchLocale('fr')}
        className={`px-2 py-1 text-xs font-ui font-semibold rounded-sm transition-colors ${
          locale === 'fr'
            ? 'bg-primary text-white'
            : 'text-foreground-muted hover:text-foreground-dark'
        }`}
        aria-label="Français"
      >
        FR
      </button>
      <button
        onClick={() => switchLocale('en')}
        className={`px-2 py-1 text-xs font-ui font-semibold rounded-sm transition-colors ${
          locale === 'en'
            ? 'bg-primary text-white'
            : 'text-foreground-muted hover:text-foreground-dark'
        }`}
        aria-label="English"
      >
        EN
      </button>
    </div>
  );
}
