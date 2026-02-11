'use client';

import { useTranslations } from 'next-intl';
import { SectionTitle } from '@/components/ui/SectionTitle';

interface FAQProps {
  namespace?: string;
  count?: number;
  altBackground?: boolean;
}

export function FAQ({
  namespace = 'landing.faq',
  count = 6,
  altBackground = true,
}: FAQProps) {
  const t = useTranslations(namespace);

  return (
    <section
      className={`py-24 ${altBackground ? 'bg-background-alt' : ''}`}
      id="faq"
    >
      <div className="max-w-narrow mx-auto px-8">
        <SectionTitle>{t('title')}</SectionTitle>
        <div className="flex flex-col gap-2">
          {Array.from({ length: count }).map((_, i) => (
            <details
              key={i}
              className="bg-white border border-border rounded-md overflow-hidden transition-shadow duration-200 hover:shadow-sm group"
            >
              <summary className="font-ui font-semibold text-[0.95rem] text-foreground-dark px-8 py-6 cursor-pointer flex justify-between items-center">
                <span>{t(`items.${i}.question`)}</span>
                <span className="text-[1.3rem] text-foreground-muted flex-shrink-0 ml-4 group-open:hidden">
                  +
                </span>
                <span className="text-[1.3rem] text-foreground-muted flex-shrink-0 ml-4 hidden group-open:inline">
                  −
                </span>
              </summary>
              <p className="px-8 pb-6 text-foreground leading-[1.7]">
                {t(`items.${i}.answer`)}
              </p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
