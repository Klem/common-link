import { useTranslations } from 'next-intl';
import { PageHero } from '@/components/sections/PageHero';
import { SectionTitle } from '@/components/ui/SectionTitle';
import { Card } from '@/components/ui/Card';

const benefitIcons = [
  // Users icon
  <svg key="users" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M23 21v-2a4 4 0 00-3-3.87" />
    <path d="M16 3.13a4 4 0 010 7.75" />
  </svg>,
  // Screen icon
  <svg key="screen" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <rect x="2" y="3" width="20" height="14" rx="2" />
    <line x1="8" y1="21" x2="16" y2="21" />
    <line x1="12" y1="17" x2="12" y2="21" />
  </svg>,
  // Check icon
  <svg key="check" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="20 6 9 17 4 12" />
  </svg>,
];

export default function AssociationsPage() {
  const t = useTranslations('associations');

  return (
    <main>
      {/* Hero */}
      <PageHero
        badge={t('hero.badge')}
        title={t('hero.title')}
        description={t('hero.description')}
      />

      {/* Benefits */}
      <section className="py-24">
        <div className="max-w-container mx-auto px-8">
          <SectionTitle>{t('benefits.title')}</SectionTitle>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {[0, 1, 2].map((i) => (
              <Card key={i} variant="feature" icon={benefitIcons[i]}>
                <h3 className="mb-2">{t(`benefits.items.${i}.title`)}</h3>
                <p className="text-foreground-muted mx-auto">
                  {t(`benefits.items.${i}.text`)}
                </p>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* Search & Registration — placeholder for Prompt 7 */}
      <section className="py-24 bg-background-alt" id="inscription">
        <div className="max-w-container mx-auto px-8">
          <SectionTitle>{t('search.title')}</SectionTitle>
          <p className="text-center mx-auto text-[1.1rem] leading-[1.75] mb-12">
            {t('search.description')}
          </p>
          <div className="max-w-[800px] mx-auto">
            <div className="flex items-center bg-white border-2 border-border rounded-lg overflow-hidden transition-all duration-200 focus-within:border-secondary focus-within:shadow-[0_0_0_4px_var(--color-secondary-pale),var(--shadow-lg)]">
              <span className="px-6 text-foreground-muted text-xl flex-shrink-0">
                🔍
              </span>
              <input
                type="text"
                className="flex-1 border-none bg-transparent py-4 font-ui text-base text-foreground-dark outline-none placeholder:text-foreground-muted"
                placeholder={t('search.placeholder')}
                disabled
              />
              <button
                className="bg-primary border-none px-7 py-4 text-white font-ui font-semibold text-[0.9rem] cursor-not-allowed opacity-50"
                disabled
              >
                {t('search.button')}
              </button>
            </div>
            <p className="text-center text-foreground-muted font-ui text-sm mt-6">
              Moteur de recherche SIRENE — bientôt disponible
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
