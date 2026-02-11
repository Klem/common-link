import { useTranslations } from 'next-intl';

export default function DonateursPage() {
  const t = useTranslations('nav');

  return (
    <main>
      <section className="py-24 text-center">
        <div className="max-w-container mx-auto px-8">
          <h1>{t('donors')}</h1>
          <p className="text-foreground-muted mt-4">Page en construction</p>
        </div>
      </section>
    </main>
  );
}
