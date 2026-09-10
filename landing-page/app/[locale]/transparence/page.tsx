import { getTranslations } from 'next-intl/server';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * Page Transparence.
 * Markup, classes et styles inline repris à l'identique de la maquette
 * (`p6-transparence.html` : `.transparency-page-hero`, `.tp-switch`, `.tp-two`,
 * `.tp-box`, `.tp-five`, `.grid-3`, `.proof-card`).
 * Ne pas remplacer par des classes Tailwind : le CSS de la maquette est la source de vérité.
 */
interface ExclusionCard {
  title: string;
  items: string[];
}

const H3_48 = { fontSize: '22px', margin: '48px 0 12px' } as const;
const H3_44 = { fontSize: '22px', margin: '44px 0 12px' } as const;

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

      <div className="transparency-page-hero" style={{ paddingBottom: '40px' }}>
        <div className="max-w" style={{ textAlign: 'center' }}>
          <div className="section-label" style={{ color: 'var(--bright-teal)' }}>
            {t('hero.label')}
          </div>
          <h1 style={{ fontSize: '50px', color: 'var(--white)', marginBottom: '18px' }}>
            {t('hero.title')}
            <br />
            <span className="italic-accent" style={{ color: 'var(--bright-teal)' }}>
              {t('hero.titleAccent')}
            </span>
          </h1>
          <p
            style={{
              fontSize: '17px',
              color: 'rgba(255,255,255,0.72)',
              maxWidth: '640px',
              margin: '0 auto 28px',
              lineHeight: 1.7,
            }}
          >
            {t('hero.text')}
          </p>
          <div className="tp-switch">
            <a href="#tp-qui">{t('hero.switchQui')}</a>
            <a href="#tp-exclus">{t('hero.switchExclus')}</a>
            <a href="#tp-fonds">{t('hero.switchFonds')}</a>
          </div>
        </div>
      </div>

      <section className="section bg-white" id="tp-qui">
        <div className="max-w" style={{ maxWidth: '900px' }}>
          <div className="section-label">{t('qui.label')}</div>
          <h2 style={{ fontSize: '30px', marginBottom: '20px' }}>{t('qui.title')}</h2>
          <p>{t('qui.intro')}</p>
          <div className="tp-two">
            <div className="tp-box ok">
              <div className="tp-box-title">{t('qui.okTitle')}</div>
              <ul className="legal-list">
                {okItems.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </div>
            <div className="tp-box no">
              <div className="tp-box-title">{t('qui.noTitle')}</div>
              <ul className="legal-list">
                {noItems.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </div>
          </div>

          <h3 style={H3_48}>{t('qui.checkTitle')}</h3>
          <p>{t('qui.checkText')}</p>
          <div className="legal-callout">{t('qui.checkCallout')}</div>

          <h3 style={H3_48}>{t('qui.acceptTitle')}</h3>
          <p>{t('qui.acceptText1')}</p>
          <p>{t('qui.acceptText2')}</p>
          <div className="tp-five">
            {criteriaItems.map((item, i) => (
              <span key={i}>{item}</span>
            ))}
          </div>
          <p>{t('qui.generalInterest')}</p>
        </div>
      </section>

      <section className="section bg-cream" id="tp-exclus">
        <div className="max-w" style={{ maxWidth: '1040px' }}>
          <div className="section-label">{t('exclus.label')}</div>
          <h2 style={{ fontSize: '30px', marginBottom: '32px' }}>{t('exclus.title')}</h2>
          <div className="grid-3">
            {cards.map((card, i) => (
              <div className="proof-card" key={i}>
                <h3 style={{ fontSize: '17px', marginBottom: '14px' }}>{card.title}</h3>
                <ul className="legal-list" style={{ fontSize: '14px' }}>
                  {card.items.map((item, j) => (
                    <li key={j}>{item}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <h3 style={H3_48}>{t('exclus.withdrawTitle')}</h3>
          <p>{t('exclus.withdrawText1')}</p>
          <p>{t('exclus.withdrawText2')}</p>
          <p>{t('exclus.withdrawText3')}</p>
          <p>{t('exclus.withdrawText4')}</p>
        </div>
      </section>

      <section className="section bg-white" id="tp-fonds">
        <div className="max-w" style={{ maxWidth: '900px' }}>
          <div className="section-label">{t('fonds.label')}</div>
          <h2 style={{ fontSize: '30px', marginBottom: '20px' }}>{t('fonds.title')}</h2>
          <p>{t('fonds.text1')}</p>
          <div className="legal-callout">{t('fonds.callout')}</div>
          <p>{t('fonds.text2')}</p>

          <h3 style={H3_44}>{t('fonds.capTitle')}</h3>
          <p>{t('fonds.capText1')}</p>
          <p>{t('fonds.capText2')}</p>

          <h3 style={H3_44}>{t('fonds.spendTitle')}</h3>
          <p>{t('fonds.spendText1')}</p>
          <p>{t('fonds.spendText2')}</p>

          <h3 style={H3_44}>{t('fonds.closeTitle')}</h3>
          <p>{t('fonds.closeText1')}</p>
          <p>{t('fonds.closeText2')}</p>
        </div>
      </section>
    </main>
  );
}
