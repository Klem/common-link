import { Fragment } from 'react';
import { getTranslations } from 'next-intl/server';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * Mentions légales.
 * Markup et classes repris à l'identique de la maquette (`p13-mentions.html` :
 * `.legal-subnav`, `.legal-hero`, `.legal-body`).
 * Ne pas remplacer par des classes Tailwind : le CSS de la maquette est la source de vérité.
 */
interface MentionsSection {
  title: string;
  paragraphs: string[];
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.mentionsLegales' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/mentions-legales',
      languages: { fr: '/mentions-legales', en: '/en/mentions-legales' },
    },
  };
}

export default async function MentionsLegalesPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.mentionsLegales' });
  const sections = t.raw('sections') as MentionsSection[];

  return (
    <main>
      <LegalSubnav active="mentions" />

      <div className="legal-hero">
        <div className="max-w">
          <h1>{t('title')}</h1>
          <p className="legal-meta">{t('lastUpdated')}</p>
        </div>
      </div>

      <div className="legal-body">
        {sections.map((section, i) => (
          <Fragment key={i}>
            <h2>{section.title}</h2>
            {section.paragraphs.map((paragraph, j) => (
              <p key={j}>{paragraph}</p>
            ))}
          </Fragment>
        ))}
      </div>
    </main>
  );
}
