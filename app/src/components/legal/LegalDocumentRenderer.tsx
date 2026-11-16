import { useTranslations } from 'next-intl';
import {
  LegalContentFormat,
  type LegalDocumentDto,
  type LegalParagraph,
  type LegalTextV1,
} from '@/types/legal';

/**
 * Parses a `JSON_V1` document into its structured text. `null` for a plain-text document, or for
 * content that does not parse — the caller then falls back on the raw text.
 */
export function parseLegalText(document: LegalDocumentDto): LegalTextV1 | null {
  if (document.format !== LegalContentFormat.JSON_V1) return null;
  try {
    return JSON.parse(document.content) as LegalTextV1;
  } catch {
    return null;
  }
}

interface Props {
  document: LegalDocumentDto;
  /** Prefix of the article anchors — keeps them unique if two texts ever share a page. */
  idPrefix?: string;
}

/**
 * Renders a CGU/CGV text the same way the landing site does (`/conditions-generales-utilisation*`):
 * effective date, table of contents, intro, then each article with its callout, paragraphs and
 * numbered subsections. The stored text carries no HTML: links are segments with an absolute URL,
 * opened in a new tab so the reader never leaves the donation or publication flow.
 *
 * The title is not rendered here — each container shows it in its own header (see `legalTitle`).
 * Plain-text documents (the first placeholder versions) are shown as pre-wrapped text.
 */
export function LegalDocumentRenderer({ document, idPrefix = `legal-${document.documentType}` }: Props) {
  const t = useTranslations('legal');
  const text = parseLegalText(document);

  if (!text) {
    return (
      <div className="legal-doc">
        <p className="legal-doc-meta">{t('version', { version: document.version })}</p>
        <div className="legal-doc-plain">{document.content}</div>
      </div>
    );
  }

  return (
    <div className="legal-doc">
      <p className="legal-doc-meta">{text.lastUpdated}</p>

      <nav className="legal-doc-toc" aria-label={t('tocTitle')}>
        <strong>{t('tocTitle')}</strong>
        <ol>
          {text.articles.map((article, i) => (
            <li key={i}>
              <a href={`#${idPrefix}-a${i}`}>{article.title}</a>
            </li>
          ))}
        </ol>
      </nav>

      <p>{text.intro}</p>

      {text.articles.map((article, i) => (
        <section key={i}>
          <h3 id={`${idPrefix}-a${i}`}>{article.title}</h3>
          {article.callout && <div className="legal-doc-callout">{article.callout}</div>}
          {article.paragraphs?.map((paragraph, j) => <Paragraph key={j} paragraph={paragraph} />)}
          {article.subsections?.map((subsection, j) => (
            <div key={`s${j}`}>
              <h4>{subsection.title}</h4>
              {subsection.paragraphs.map((paragraph, k) => <Paragraph key={k} paragraph={paragraph} />)}
            </div>
          ))}
        </section>
      ))}
    </div>
  );
}

function Paragraph({ paragraph }: { paragraph: LegalParagraph }) {
  return (
    <p>
      {paragraph.map((segment, i) =>
        segment.href ? (
          <a key={i} href={segment.href} target="_blank" rel="noopener noreferrer">
            {segment.text}
          </a>
        ) : (
          <span key={i}>{segment.text}</span>
        ),
      )}
    </p>
  );
}

/** The document's own title for a structured text, else the generic label of its type. */
export function legalTitle(document: LegalDocumentDto | null, fallback: string): string {
  return (document && parseLegalText(document)?.title) || fallback;
}
