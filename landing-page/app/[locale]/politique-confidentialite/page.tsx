import { Fragment, type ReactNode } from 'react';
import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * Privacy policy page.
 * Markup and classes taken verbatim from the mockup (`p19-confidentialite.html`:
 * `.legal-subnav`, `.legal-hero`, `.legal-body`, `.legal-table`).
 * Do not replace with Tailwind classes: the mockup CSS is the source of truth.
 *
 * The texts (`legal.politiqueConfidentialite` in `messages/fr.json`) are also the privacy policy stored and
 * accepted in the app (document type `PRIVACY`): the API bundles this file and injects every change at startup
 * as a new version (`api/.../bootstrap/LegalDocumentSeeder.kt`). Same shape as the CGU texts, plus an optional
 * `table` rendered before an article's paragraphs, and `<b>…</b>` for bold runs.
 */
interface Table {
  head: string[];
  rows: string[][];
}

interface Subsection {
  title: string;
  paragraphs: string[];
}

interface Article {
  title: string;
  table?: Table;
  paragraphs?: string[];
  subsections?: Subsection[];
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.privacy' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/politique-confidentialite',
      languages: { fr: '/politique-confidentialite', en: '/en/politique-confidentialite' },
    },
  };
}

export default async function PolitiqueConfidentialitePage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.politiqueConfidentialite' });
  const tl = await getTranslations({ locale, namespace: 'legal' });
  const articles = t.raw('articles') as Article[];
  // Links are written `<cookies>…</cookies>` in the texts; `legal.linkTargets` maps each tag to its page.
  // The API resolves the same tags the same way, so both renderings carry the same links.
  const linkTargets = tl.raw('linkTargets') as Record<string, string>;
  const richTags = {
    ...Object.fromEntries(
      Object.entries(linkTargets).map(([tag, href]) => [
        tag,
        (chunks: ReactNode) => (
          <Link href={href} className="legal-link">
            {chunks}
          </Link>
        ),
      ]),
    ),
    b: (chunks: ReactNode) => <strong>{chunks}</strong>,
  };
  /** Plain paragraphs are rendered as-is; a paragraph carrying tags is resolved by `t.rich` from its path. */
  const paragraph = (text: string, path: string) => (text.includes('<') ? t.rich(path, richTags) : text);

  return (
    <main>
      {/* The mockup scopes the legal-page classes under this container. */}
      <div className="legal-page">
        <LegalSubnav active="privacy" />

        <div className="legal-hero">
          <div className="max-w">
            <h1>{t('title')}</h1>
            <p className="legal-meta">{t('lastUpdated')}</p>
          </div>
        </div>

        <div className="legal-body">
          {articles.map((article, i) => (
            <Fragment key={i}>
              <h2>{article.title}</h2>
              {article.table && (
                <table className="legal-table">
                  <thead>
                    <tr>
                      {article.table.head.map((cell, j) => (
                        <th key={j}>{cell}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {article.table.rows.map((row, j) => (
                      <tr key={j}>
                        {row.map((cell, k) => (
                          <td key={k}>{cell}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {article.paragraphs?.map((text, j) => (
                <p key={j}>{paragraph(text, `articles.${i}.paragraphs.${j}`)}</p>
              ))}
              {article.subsections?.map((subsection, j) => (
                <Fragment key={j}>
                  <h3>{subsection.title}</h3>
                  {subsection.paragraphs.map((text, k) => (
                    <p key={k}>{paragraph(text, `articles.${i}.subsections.${j}.paragraphs.${k}`)}</p>
                  ))}
                </Fragment>
              ))}
            </Fragment>
          ))}
        </div>
      </div>
    </main>
  );
}
