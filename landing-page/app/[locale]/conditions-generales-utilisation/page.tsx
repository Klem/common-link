import { Fragment, type ReactNode } from 'react';
import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * CGU for donors.
 * Markup and classes taken verbatim from the mockup (`p14-cgu-donateur.html`:
 * `.legal-subnav`, `.legal-hero`, `.legal-body`, `.legal-toc-auto`).
 * Do not replace with Tailwind classes: the mockup CSS is the source of truth.
 *
 * The texts (`legal.cguDonors` in `messages/fr.json`) are also the CGU stored and accepted in the app:
 * the API bundles this file and injects every change at startup as a new version
 * (`api/.../bootstrap/LegalDocumentSeeder.kt`).
 */
interface Article {
  title: string;
  callout?: string;
  paragraphs?: string[];
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.cguDonors' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/conditions-generales-utilisation',
      languages: { fr: '/conditions-generales-utilisation', en: '/en/conditions-generales-utilisation' },
    },
  };
}

export default async function CguDonorsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.cguDonors' });
  const tl = await getTranslations({ locale, namespace: 'legal' });
  const articles = t.raw('articles') as Article[];
  // Links are written `<privacy>…</privacy>` in the texts; `legal.linkTargets` maps each tag to its page.
  // The API resolves the same tags the same way, so both renderings carry the same links.
  const linkTargets = tl.raw('linkTargets') as Record<string, string>;
  const linkTags = Object.fromEntries(
    Object.entries(linkTargets).map(([tag, href]) => [
      tag,
      (chunks: ReactNode) => (
        <Link href={href} className="legal-link">
          {chunks}
        </Link>
      ),
    ]),
  );

  return (
    <main>
      {/* The mockup scopes the legal-page classes under this container. */}
      <div className="legal-page">
        <LegalSubnav active="cguDonors" />

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
                  <a href={`#p14-a${i}`}>{article.title}</a>
                </li>
              ))}
            </ol>
          </div>

          <p>{t('intro')}</p>

          {articles.map((article, i) => (
            <Fragment key={i}>
              <h3 id={`p14-a${i}`}>{article.title}</h3>
              {article.callout && <p>{article.callout}</p>}
              {article.paragraphs?.map((paragraph, j) => (
                <p key={j}>
                  {paragraph.includes('<') ? t.rich(`articles.${i}.paragraphs.${j}`, linkTags) : paragraph}
                </p>
              ))}
            </Fragment>
          ))}
        </div>
      </div>
    </main>
  );
}
