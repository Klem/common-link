import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LegalDocumentRenderer, legalTitle, parseLegalText } from '../LegalDocumentRenderer';
import { LegalContentFormat, LegalDocumentType, type LegalDocumentDto, type LegalTextV1 } from '@/types/legal';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const text: LegalTextV1 = {
  title: 'Conditions générales d\'utilisation applicables aux donateurs',
  lastUpdated: 'Version en vigueur au 9 septembre 2026',
  intro: 'Préambule.',
  articles: [
    {
      title: 'Article 1. Objet',
      callout: 'À retenir.',
      paragraphs: [[
        { text: 'Voir la ' },
        { text: 'politique de confidentialité', href: 'https://www.common-link.org/politique-confidentialite' },
        { text: ' du site.' },
      ]],
    },
    {
      title: 'Annexe 1. Contrat type',
      subsections: [{ title: '1. Objet', paragraphs: [[{ text: 'Texte de la sous-section.' }]] }],
    },
  ],
};

const jsonDoc: LegalDocumentDto = {
  documentType: LegalDocumentType.CGU,
  version: '2026-09-09-0123456789ab',
  format: LegalContentFormat.JSON_V1,
  content: JSON.stringify(text),
  publishedAt: '2026-11-16T00:00:00Z',
};

describe('LegalDocumentRenderer', () => {
  it('renders a structured text like the landing site: date, contents, intro, articles', () => {
    render(<LegalDocumentRenderer document={jsonDoc} />);

    expect(screen.getByText('Version en vigueur au 9 septembre 2026')).toBeInTheDocument();
    expect(screen.getByText('tocTitle', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByText('Préambule.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: 'Article 1. Objet' })).toHaveAttribute('id', 'legal-CGU-a0');
    expect(screen.getByRole('link', { name: 'Article 1. Objet' })).toHaveAttribute('href', '#legal-CGU-a0');
    expect(screen.getByText('À retenir.')).toHaveClass('legal-doc-callout');
    expect(screen.getByRole('heading', { level: 4, name: '1. Objet' })).toBeInTheDocument();
    expect(screen.getByText('Texte de la sous-section.')).toBeInTheDocument();
  });

  it('renders link segments as external links opened in a new tab', () => {
    render(<LegalDocumentRenderer document={jsonDoc} />);

    const link = screen.getByRole('link', { name: 'politique de confidentialité' });
    expect(link).toHaveAttribute('href', 'https://www.common-link.org/politique-confidentialite');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('falls back on plain text for a TEXT document', () => {
    render(
      <LegalDocumentRenderer
        document={{ ...jsonDoc, format: LegalContentFormat.TEXT, version: '2026-08-26', content: 'Texte brut.' }}
      />,
    );

    expect(screen.getByText('Texte brut.')).toHaveClass('legal-doc-plain');
    expect(screen.queryByText('tocTitle')).not.toBeInTheDocument();
  });

  it('falls back on plain text when the structured content does not parse', () => {
    render(<LegalDocumentRenderer document={{ ...jsonDoc, content: '{not json' }} />);

    expect(screen.getByText('{not json')).toBeInTheDocument();
  });
});

describe('parseLegalText / legalTitle', () => {
  it('parses JSON_V1 only', () => {
    expect(parseLegalText(jsonDoc)?.articles).toHaveLength(2);
    expect(parseLegalText({ ...jsonDoc, format: LegalContentFormat.TEXT })).toBeNull();
  });

  it('prefers the document title, else the fallback label', () => {
    expect(legalTitle(jsonDoc, 'fallback')).toBe(text.title);
    expect(legalTitle({ ...jsonDoc, format: LegalContentFormat.TEXT }, 'fallback')).toBe('fallback');
    expect(legalTitle(null, 'fallback')).toBe('fallback');
  });
});
