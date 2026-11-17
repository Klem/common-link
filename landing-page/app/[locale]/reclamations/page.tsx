import { Fragment } from 'react';
import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * Complaints page.
 * Markup and classes taken verbatim from the mockup (`p17-reclamations.html`:
 * `.legal-subnav`, `.legal-hero`, `.legal-body`, `.legal-list`). The delay note is a plain
 * paragraph, as in the mockup.
 * Do not replace with Tailwind classes: the mockup CSS is the source of truth.
 */
interface ComplaintSection {
  title: string;
  paragraphs?: string[];
  items?: string[];
  callout?: string;
  paragraphsAfter?: string[];
}

/** Paragraphs holding an internal link, keyed by `section.paragraph`. */
const INLINE_LINKS: Record<string, string> = {
  '6.0': '/politique-confidentialite',
};

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.complaints' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/reclamations',
      languages: { fr: '/reclamations', en: '/en/reclamations' },
    },
  };
}

export default async function ComplaintsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.complaints' });
  const sections = t.raw('sections') as ComplaintSection[];

  return (
    <main>
      {/* The mockup scopes the legal-page classes under this container. */}
      <div className="legal-page">
        <LegalSubnav active="complaints" />

        <div className="legal-hero">
          <div className="max-w">
            <h1>{t('title')}</h1>
            <p className="legal-meta">{t('meta')}</p>
          </div>
        </div>

        <div className="legal-body">
          <p>{t('intro')}</p>

          {sections.map((section, i) => (
            <Fragment key={i}>
              <h2>{section.title}</h2>
              {section.items && (
                <ul className="legal-list">
                  {section.items.map((item, j) => (
                    <li key={j}>{item}</li>
                  ))}
                </ul>
              )}
              {section.paragraphs?.map((paragraph, j) => {
                const href = INLINE_LINKS[`${i}.${j}`];
                return (
                  <p key={j}>
                    {href
                      ? t.rich(`sections.${i}.paragraphs.${j}`, {
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
              {section.callout && <p>{section.callout}</p>}
              {section.paragraphsAfter?.map((paragraph, j) => (
                <p key={j}>{paragraph}</p>
              ))}
            </Fragment>
          ))}
        </div>
      </div>
    </main>
  );
}
