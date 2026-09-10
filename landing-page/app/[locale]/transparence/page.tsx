import { getTranslations } from 'next-intl/server';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

interface ExclusionCard {
  title: string;
  items: string[];
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.transparency' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/transparence',
      languages: { fr: '/transparence', en: '/en/transparence' },
    },
  };
}

export default async function TransparencyPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'transparency' });
  const cards = t.raw('exclus.cards') as ExclusionCard[];
  const okItems = t.raw('qui.okItems') as string[];
  const noItems = t.raw('qui.noItems') as string[];
  const criteriaItems = t.raw('qui.criteriaItems') as string[];

  return (
    <main>
      <LegalSubnav active="transparency" />

      <section className="bg-primary text-white text-center py-20 px-8">
        <div className="max-w-narrow mx-auto">
          <div className="font-ui text-[0.85rem] font-semibold text-secondary uppercase tracking-wider mb-3">
            {t('hero.label')}
          </div>
          <h1 className="font-ui text-[2.2rem] md:text-[3.1rem] font-extrabold text-white mb-4">
            {t('hero.title')}
            <br />
            <span className="italic-accent text-secondary">{t('hero.titleAccent')}</span>
          </h1>
          <p className="text-white/70 text-[1.05rem] leading-relaxed mb-6">{t('hero.text')}</p>
          <div className="flex flex-wrap justify-center gap-6 text-[0.9rem] font-ui font-semibold">
            <a href="#tp-qui" className="text-secondary hover:text-white transition-colors">{t('hero.switchQui')}</a>
            <a href="#tp-exclus" className="text-secondary hover:text-white transition-colors">{t('hero.switchExclus')}</a>
            <a href="#tp-fonds" className="text-secondary hover:text-white transition-colors">{t('hero.switchFonds')}</a>
          </div>
        </div>
      </section>

      <section id="tp-qui" className="py-20 px-8 bg-white">
        <div className="max-w-narrow mx-auto legal-prose">
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('qui.label')}</div>
          <h2>{t('qui.title')}</h2>
          <p>{t('qui.intro')}</p>

          <div className="grid md:grid-cols-2 gap-6 my-8">
            <div className="rounded-lg border border-border bg-background p-6">
              <div className="font-ui font-bold text-foreground-dark mb-3">{t('qui.okTitle')}</div>
              <ul className="space-y-2 text-[0.9rem] text-foreground">
                {okItems.map((item, i) => <li key={i}>{item}</li>)}
              </ul>
            </div>
            <div className="rounded-lg border border-border bg-background p-6">
              <div className="font-ui font-bold text-foreground-dark mb-3">{t('qui.noTitle')}</div>
              <ul className="space-y-2 text-[0.9rem] text-foreground">
                {noItems.map((item, i) => <li key={i}>{item}</li>)}
              </ul>
            </div>
          </div>

          <h3>{t('qui.checkTitle')}</h3>
          <p>{t('qui.checkText')}</p>
          <div className="legal-callout">{t('qui.checkCallout')}</div>

          <h3>{t('qui.acceptTitle')}</h3>
          <p>{t('qui.acceptText1')}</p>
          <p>{t('qui.acceptText2')}</p>
          <div className="flex flex-wrap gap-2 my-4">
            {criteriaItems.map((item, i) => (
              <span key={i} className="inline-flex px-3 py-1.5 rounded-full bg-background-alt text-foreground-dark text-[0.85rem] font-ui font-medium">
                {item}
              </span>
            ))}
          </div>
          <p>{t('qui.generalInterest')}</p>
        </div>
      </section>

      <section id="tp-exclus" className="py-20 px-8 bg-background">
        <div className="max-w-container mx-auto legal-prose">
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('exclus.label')}</div>
          <h2>{t('exclus.title')}</h2>
          <div className="grid md:grid-cols-3 gap-6 my-8">
            {cards.map((card, i) => (
              <div key={i} className="rounded-lg border border-border bg-white p-6">
                <div className="font-ui font-bold text-foreground-dark mb-3 text-[1.05rem]">{card.title}</div>
                <ul className="space-y-3 text-[0.85rem] text-foreground leading-relaxed">
                  {card.items.map((item, j) => <li key={j}>{item}</li>)}
                </ul>
              </div>
            ))}
          </div>

          <h3>{t('exclus.withdrawTitle')}</h3>
          <p>{t('exclus.withdrawText1')}</p>
          <p>{t('exclus.withdrawText2')}</p>
          <p>{t('exclus.withdrawText3')}</p>
          <p>{t('exclus.withdrawText4')}</p>
        </div>
      </section>

      <section id="tp-fonds" className="py-20 px-8 bg-white">
        <div className="max-w-narrow mx-auto legal-prose">
          <div className="font-ui text-[0.8rem] font-semibold text-secondary uppercase tracking-wider mb-2">{t('fonds.label')}</div>
          <h2>{t('fonds.title')}</h2>
          <p>{t('fonds.text1')}</p>
          <div className="legal-callout">{t('fonds.callout')}</div>
          <p>{t('fonds.text2')}</p>

          <h3>{t('fonds.capTitle')}</h3>
          <p>{t('fonds.capText1')}</p>
          <p>{t('fonds.capText2')}</p>

          <h3>{t('fonds.spendTitle')}</h3>
          <p>{t('fonds.spendText1')}</p>
          <p>{t('fonds.spendText2')}</p>

          <h3>{t('fonds.closeTitle')}</h3>
          <p>{t('fonds.closeText1')}</p>
          <p>{t('fonds.closeText2')}</p>
        </div>
      </section>
    </main>
  );
}
