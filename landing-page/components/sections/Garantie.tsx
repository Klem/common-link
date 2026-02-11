import { useTranslations } from 'next-intl';
import { SectionTitle } from '@/components/ui/SectionTitle';

export function Garantie() {
  const t = useTranslations('landing.guarantee');

  return (
    <section className="py-24 bg-background-alt">
      <div className="max-w-narrow mx-auto px-8">
        <SectionTitle>{t('title')}</SectionTitle>
        <p className="text-center mx-auto text-[1.1rem] leading-[1.75]">
          {t('text')}
        </p>
      </div>
    </section>
  );
}
