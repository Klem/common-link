import { Fragment, type ReactNode } from 'react';
import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * CGU for beneficiary organisations.
 * Markup and classes taken verbatim from the mockup (`p15-cgu-asso.html`:
 * `.legal-subnav`, `.legal-hero`, `.legal-body`, `.legal-toc-auto`).
 * Do not replace with Tailwind classes: the mockup CSS is the source of truth.
 *
 * The texts (`legal.cguAssociations` in `messages/fr.json`) are also the document stored as `CGV` and
 * accepted in the app: the API bundles this file and injects every change at startup as a new version
 * (`api/.../bootstrap/LegalDocumentSeeder.kt`).
 */
interface Subsection {
  title: string;
  paragraphs: string[];
}

interface Article {
  title: string;
  callout?: string;
  paragraphs?: string[];
  /** Numbered inner sections, used by annex 2 only. */
  subsections?: Subsection[];
}

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
            {article.paragraphs?.map((paragraph, j) => (
              <p key={j}>
                {paragraph.includes('<') ? t.rich(`articles.${i}.paragraphs.${j}`, linkTags) : paragraph}
              </p>
            ))}
            {article.subsections?.map((subsection, j) => (
              <Fragment key={`s${j}`}>
                <h4>{subsection.title}</h4>
                {subsection.paragraphs.map((paragraph, k) => (
                  <p key={k}>{paragraph}</p>
                ))}
              </Fragment>
            ))}
          </Fragment>
        ))}
      </div>
    </main>
  );
}
