import { useTranslations } from 'next-intl';
import { getTranslations } from 'next-intl/server';
import { PageHero } from '@/components/sections/PageHero';
import { SectionTitle } from '@/components/ui/SectionTitle';
import { Card } from '@/components/ui/Card';
import { FAQ } from '@/components/sections/FAQ';
import { Button } from '@/components/ui/Button';
import { DashboardMockup } from '@/components/donors/DashboardMockup';
import { SignupForm } from '@/components/donors/SignupForm';

const previewIcons = [
  // Pulse / real-time tracking
  <svg key="pulse" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>,
  // Document + check / tax receipt
  <svg key="receipt" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
    <polyline points="14 2 14 8 20 8" />
    <path d="M9 15l2 2 4-4" />
  </svg>,
  // Clock / verified history
  <svg key="clock" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" />
    <polyline points="12 6 12 12 16 14" />
  </svg>,
];

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.donors' });
  return {
    title: t('title'),
    description: t('description'),
    keywords: t('keywords'),
    openGraph: {
      title: t('title'),
      description: t('description'),
      type: 'website',
    },
    alternates: {
      languages: { fr: '/donateurs', en: '/en/donors' },
    },
  };
}

export default function DonateursPage() {
  const t = useTranslations('donors');

  return (
    <main>
      {/* Hero */}
      <PageHero
        badge={t('hero.badge')}
        title={t('hero.title')}
        description={t('hero.description')}
      />

      {/* Value Preview - 3 cards */}
      <section className="py-24">
        <div className="max-w-container mx-auto px-8">
          <SectionTitle>{t('preview.title')}</SectionTitle>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {[0, 1, 2].map((i) => (
              <Card key={i} variant="preview" icon={previewIcons[i]}>
                <h4 className="mb-2">{t(`preview.items.${i}.title`)}</h4>
                <p className="text-foreground-muted mx-auto">
                  {t(`preview.items.${i}.text`)}
                </p>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* Dashboard Mockup */}
      <section className="py-24 bg-background-alt">
        <div className="max-w-container mx-auto px-8">
          <DashboardMockup />
        </div>
      </section>

      {/* Signup Form */}
      <section className="py-24" id="inscription">
        <div className="max-w-container mx-auto px-8">
          <SectionTitle>{t('signup.title')}</SectionTitle>
          <p className="text-center mx-auto text-[1.1rem] leading-[1.75] mb-12 max-w-[500px]">
            {t('signup.description')}
          </p>
          <SignupForm />
        </div>
      </section>

      {/* FAQ */}
      <FAQ namespace="donors.faq" count={5} altBackground={true} />

      {/* CTA Final */}
      <section className="py-24 text-center">
        <div className="max-w-container mx-auto px-8">
          <h2>{t('cta.title')}</h2>
          <p className="max-w-[500px] mx-auto mt-6 mb-10 text-foreground-muted text-[1.05rem]">
            {t('cta.description')}
          </p>
          <Button variant="primary" size="lg" href="#inscription">
            {t('cta.button')}
          </Button>
        </div>
      </section>
    </main>
  );
}
