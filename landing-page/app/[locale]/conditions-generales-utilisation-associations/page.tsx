import { Fragment, type ReactNode } from 'react';
import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

/**
 * CGU for beneficiary organisations.
 * Markup and classes taken verbatim from the mockup (`p15-cgu-asso.html`:
 * `.legal-subnav`, `.legal-hero`, `.legal-body`, `.legal-toc-auto`, `.legal-list`).
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

/** Prefix of a list item in the texts (`– …`); the mockup renders those lines as a `.legal-list`. */
const LIST_ITEM = '– ';

type Block = { kind: 'paragraph'; index: number } | { kind: 'list'; indexes: number[] };

/**
 * Groups consecutive list items of an article into one list block, every other paragraph staying a
 * block of its own. Blocks keep the paragraph indexes: rich paragraphs are translated by their path
 * in `messages/fr.json`. A line carrying tags stays a paragraph (its tags are resolved by `t.rich`).
 * The texts themselves are untouched, so the version stored by the API does not change.
 */
function toBlocks(paragraphs: string[]): Block[] {
  const blocks: Block[] = [];
  paragraphs.forEach((paragraph, index) => {
    if (!paragraph.startsWith(LIST_ITEM) || paragraph.includes('<')) {
      blocks.push({ kind: 'paragraph', index });
      return;
    }
    const last = blocks[blocks.length - 1];
    if (last?.kind === 'list') last.indexes.push(index);
    else blocks.push({ kind: 'list', indexes: [index] });
  });
  return blocks;
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
      {/* The mockup scopes the legal-page classes under this container. */}
      <div className="legal-page">
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
              {article.callout && <p>{article.callout}</p>}
              {toBlocks(article.paragraphs ?? []).map((block) =>
                block.kind === 'list' ? (
                  <ul key={`l${block.indexes[0]}`} className="legal-list">
                    {block.indexes.map((j) => (
                      <li key={j}>{article.paragraphs![j].slice(LIST_ITEM.length)}</li>
                    ))}
                  </ul>
                ) : (
                  <p key={block.index}>
                    {article.paragraphs![block.index].includes('<')
                      ? t.rich(`articles.${i}.paragraphs.${block.index}`, linkTags)
                      : article.paragraphs![block.index]}
                  </p>
                ),
              )}
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
      </div>
    </main>
  );
}
