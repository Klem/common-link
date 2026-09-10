import { Fragment } from 'react';
import { getTranslations } from 'next-intl/server';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * Contrat type entre le donateur et l'organisme bénéficiaire.
 * Markup et classes repris à l'identique de la maquette (`p16-contrat.html` :
 * `.legal-subnav`, `.legal-hero`, `.legal-body`, `.legal-toc-auto`).
 * Ne pas remplacer par des classes Tailwind : le CSS de la maquette est la source de vérité.
 */
interface Article {
  title: string;
  paragraphs?: string[];
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.modelAgreement' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/contrat-type',
      languages: { fr: '/contrat-type', en: '/en/contrat-type' },
    },
  };
}

export default async function ModelAgreementPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.modelAgreement' });
  const tl = await getTranslations({ locale, namespace: 'legal' });
  const articles = t.raw('articles') as Article[];

  return (
    <main>
      <LegalSubnav active="modelAgreement" />

      <div className="legal-hero">
        <div className="max-w">
          <h1>{t('title')}</h1>
          <p className="legal-meta">{t('meta')}</p>
        </div>
      </div>

      <div className="legal-body">
        <div className="legal-toc-auto">
          <strong>{tl('tocTitle')}</strong>
          <ol>
            {articles.map((article, i) => (
              <li key={i}>
                <a href={`#p16-a${i}`}>{article.title}</a>
              </li>
            ))}
          </ol>
        </div>

        <p>{t('intro')}</p>

        <h2>{t('parties.title')}</h2>
        <p>{t('parties.p1')}</p>
        <p>{t('parties.donor')}</p>
        <p>{t('parties.org')}</p>

        <h2>{t('intermediary.title')}</h2>
        <p>{t('intermediary.p1')}</p>
        <p>{t('intermediary.p2')}</p>

        <h2>{t('complaintsService.title')}</h2>
        <p>{t('complaintsService.p1')}</p>

        {articles.map((article, i) => (
          <Fragment key={i}>
            <h3 id={`p16-a${i}`}>{article.title}</h3>
            {article.paragraphs?.map((paragraph, j) => (
              <p key={j}>{paragraph}</p>
            ))}
          </Fragment>
        ))}
      </div>
    </main>
  );
}
