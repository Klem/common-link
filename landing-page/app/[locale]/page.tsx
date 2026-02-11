import { useTranslations } from 'next-intl';

export default function HomePage() {
  const t = useTranslations('landing');

  return (
    <main>
      <section className="py-24 text-center">
        <div className="max-w-container mx-auto px-8">
          <span className="inline-block font-ui text-sm font-semibold text-secondary bg-secondary-pale px-4 py-1.5 rounded-xl mb-6">
            {t('hero.badge')}
          </span>
          <h1 className="max-w-[700px] mx-auto mb-6">{t('hero.title')}</h1>
          <p className="text-lg text-foreground-muted max-w-[540px] mx-auto mb-8">
            {t('hero.subtitle')}
          </p>
          <p className="font-ui text-sm text-foreground-muted mt-6">
            {t('hero.launch')}
          </p>
        </div>
      </section>
    </main>
  );
}
