import { Fragment } from 'react';
import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * CGU applicables aux organismes bénéficiaires.
 * Markup et classes repris à l'identique de la maquette (`p15-cgu-asso.html` :
 * `.legal-subnav`, `.legal-hero`, `.legal-body`, `.legal-toc-auto`).
 * Ne pas remplacer par des classes Tailwind : le CSS de la maquette est la source de vérité.
 */
interface Article {
  title: string;
  callout?: string;
  paragraphs?: string[];
}

/** Paragraphes contenant un lien interne, indexés par `article.paragraphe`. */
const INLINE_LINKS: Record<string, string> = {
  '35.6': '/reclamations',
  '41.0': '/contrat-type',
};

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.cguAssociations' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/conditions-generales-utilisation-associations',
      languages: {
        fr: '/conditions-generales-utilisation-associations',
        en: '/en/conditions-generales-utilisation-associations',
      },
    },
  };
}

export default async function CguAssociationsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.cguAssociations' });
  const tl = await getTranslations({ locale, namespace: 'legal' });
  const articles = t.raw('articles') as Article[];

  return (
    <main>
      <LegalSubnav active="cguAssociations" />

      <div className="legal-hero">
        <div className="max-w">
          <h1>{t('title')}</h1>
          <p className="legal-meta">{t('lastUpdated')}</p>
        </div>
      </div>

      <div className="legal-body">
        <div className="legal-toc-auto">
          <strong>{tl('tocTitle')}</strong>
          <ol>
            {articles.map((article, i) => (
              <li key={i}>
                <a href={`#p15-a${i}`}>{article.title}</a>
              </li>
            ))}
          </ol>
        </div>

        <p>{t('intro')}</p>

        {articles.map((article, i) => (
          <Fragment key={i}>
            <h3 id={`p15-a${i}`}>{article.title}</h3>
            {article.callout && <div className="legal-callout">{article.callout}</div>}
            {article.paragraphs?.map((paragraph, j) => {
              const href = INLINE_LINKS[`${i}.${j}`];
              return (
                <p key={j}>
                  {href
                    ? t.rich(`articles.${i}.paragraphs.${j}`, {
                        link: (chunks) => (
                          <Link href={href} className="legal-link">
                            {chunks}
                          </Link>
                        ),
                      })
                    : paragraph}
                </p>
              );
            })}
          </Fragment>
        ))}
      </div>
    </main>
  );
}
