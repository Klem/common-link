import { useTranslations } from 'next-intl';
import { SectionTitle } from '@/components/ui/SectionTitle';

export function Steps() {
  const t = useTranslations('landing.steps');

  return (
    <section className="py-24" id="comment-ca-marche">
      <div className="max-w-container mx-auto px-8">
        <SectionTitle>{t('title')}</SectionTitle>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-12">
          {[0, 1, 2].map((i) => (
            <div key={i} className="text-center">
              <div className="w-12 h-12 mx-auto mb-6 bg-primary text-white rounded-full flex items-center justify-center font-ui font-bold text-[1.1rem]">
                {t(`items.${i}.number`)}
              </div>
              <h3 className="mb-2">{t(`items.${i}.title`)}</h3>
              <p className="text-foreground-muted mx-auto">
                {t(`items.${i}.text`)}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
