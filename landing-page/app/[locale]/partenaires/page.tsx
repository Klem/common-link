import { useTranslations } from 'next-intl';
import { getTranslations } from 'next-intl/server';
import { PageHero } from '@/components/sections/PageHero';
import { SectionTitle } from '@/components/ui/SectionTitle';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { ContactForm } from '@/components/sections/ContactForm';

const infrastructureIcons = [
  // Shield icon — Conformité
  <svg key="shield" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
  </svg>,
  // Clock icon — Gestion de fonds
  <svg key="clock" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="12" r="10" />
    <path d="M12 6v6l4 2" />
  </svg>,
  // Eye icon — Transparence
  <svg key="eye" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>,
];

const flowIcons: Record<string, string> = {
  money: '💶',
  convert: '🔄',
  lock: '🔐',
  chart: '📊',
  receipt: '🧾',
};

const roadmapIcons = ['🔶', '🚀', '📈'];

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.partners' });
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
      languages: { fr: '/partenaires', en: '/en/partners' },
    },
  };
}

export default function PartenairesPage() {
  const t = useTranslations('partners');

  return (
    <main>
      {/* Hero + Stats */}
      <PageHero
        badge={t('hero.badge')}
        title={t('hero.title')}
        description={t('hero.description')}
      >
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-8 max-w-[900px] mx-auto mt-16">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="text-center p-8 bg-white border border-border rounded-lg shadow-sm"
            >
              <div className="font-ui text-[2.5rem] font-extrabold text-primary leading-none mb-1">
                {t(`stats.${i}.number`)}
              </div>
              <div className="font-ui text-[0.85rem] text-foreground-muted font-medium">
                {t(`stats.${i}.label`)}
              </div>
            </div>
          ))}
        </div>
      </PageHero>

      {/* Vision & Mission */}
      <section className="py-24">
        <div className="max-w-container mx-auto px-8">
          <SectionTitle>{t('vision.title')}</SectionTitle>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            <Card variant="default">
              <h3 className="mb-4 text-primary">{t('vision.promote.title')}</h3>
              <p>{t('vision.promote.text')}</p>
            </Card>
            <Card variant="default">
              <h3 className="mb-4 text-primary">{t('vision.mission.title')}</h3>
              <p>{t('vision.mission.text')}</p>
            </Card>
          </div>
        </div>
      </section>

      {/* Architecture & flux */}
      <section className="py-24 bg-background-alt">
        <div className="max-w-container mx-auto px-8">
          <SectionTitle>{t('architecture.title')}</SectionTitle>
          <p className="text-center mx-auto text-[1.1rem] leading-[1.75] mb-16">
            {t('architecture.description')}
          </p>
          <div className="flex flex-col gap-4 max-w-[700px] mx-auto">
            {[0, 1, 2, 3, 4].map((i) => (
              <div
                key={i}
                className="flex items-start gap-6 p-8 bg-white border border-border rounded-lg relative transition-all duration-200 hover:translate-x-1 hover:shadow-md"
              >
                <div className="w-11 h-11 flex-shrink-0 bg-secondary-pale rounded-sm flex items-center justify-center text-primary text-xl">
                  {flowIcons[t(`architecture.steps.${i}.icon`)] || '📋'}
                </div>
                <div>
                  <h4 className="font-ui text-[0.95rem] font-bold mb-1">
                    {t(`architecture.steps.${i}.title`)}
                  </h4>
                  <p className="text-[0.9rem] text-foreground-muted">
                    {t(`architecture.steps.${i}.text`)}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Infrastructure & conformité */}
      <section className="py-24">
        <div className="max-w-container mx-auto px-8">
          <SectionTitle>{t('infrastructure.title')}</SectionTitle>
          <p className="text-center mx-auto text-[1.1rem] leading-[1.75] mb-16">
            {t('infrastructure.description')}
          </p>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {[0, 1, 2].map((i) => (
              <Card key={i} variant="feature" icon={infrastructureIcons[i]}>
                <h3 className="mb-2">{t(`infrastructure.items.${i}.title`)}</h3>
                <p className="text-foreground-muted mx-auto">
                  {t(`infrastructure.items.${i}.text`)}
                </p>
                <Badge variant="feature" className="mt-4">
                  {t(`infrastructure.items.${i}.tag`)}
                </Badge>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* Équipe */}
      <section className="py-24 bg-background-alt">
        <div className="max-w-container mx-auto px-8">
          <SectionTitle>{t('team.title')}</SectionTitle>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-12 max-w-[960px] mx-auto">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="text-center p-12 bg-white border border-border rounded-lg transition-all duration-[250ms] hover:-translate-y-[3px] hover:shadow-lg"
              >
                <div className="w-24 h-24 mx-auto mb-6 rounded-full bg-gradient-to-br from-primary to-secondary flex items-center justify-center text-white font-ui text-3xl font-bold border-[3px] border-secondary-pale">
                  {t(`team.members.${i}.initials`)}
                </div>
                <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-1">
                  {t(`team.members.${i}.role`)}
                </div>
                <h3 className="mb-2">{t(`team.members.${i}.name`)}</h3>
                <p className="text-[0.9rem] text-foreground-muted mx-auto">
                  {t(`team.members.${i}.bio`)}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Roadmap */}
      <section className="py-24">
        <div className="max-w-narrow mx-auto px-8">
          <SectionTitle>{t('roadmap.title')}</SectionTitle>
          <div className="flex flex-col gap-4 max-w-[700px] mx-auto">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="flex items-start gap-6 p-8 bg-white border border-border rounded-lg relative transition-all duration-200 hover:translate-x-1 hover:shadow-md"
              >
                <div
                  className={`w-11 h-11 flex-shrink-0 rounded-sm flex items-center justify-center text-xl ${
                    i === 0
                      ? 'bg-accent/[0.12] text-accent'
                      : 'bg-secondary-pale text-primary'
                  }`}
                >
                  {roadmapIcons[i]}
                </div>
                <div>
                  <h4 className="font-ui text-[0.95rem] font-bold mb-1">
                    {t(`roadmap.steps.${i}.title`)}
                  </h4>
                  <p className="text-[0.9rem] text-foreground-muted">
                    {t(`roadmap.steps.${i}.text`)}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-24 text-center bg-primary text-white">
        <div className="max-w-container mx-auto px-8">
          <h2 className="text-white mb-4">{t('cta.title')}</h2>
          <p className="text-white/75 max-w-[500px] mx-auto text-[1.1rem]">
            {t('cta.description')}
          </p>
        </div>
      </section>

      {/* Contact Form */}
      <ContactForm />
    </main>
  );
}
