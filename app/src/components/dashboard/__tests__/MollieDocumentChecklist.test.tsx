import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import MollieDocumentChecklist from '../MollieDocumentChecklist';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

describe('MollieDocumentChecklist', () => {
  it('lists the three Mollie requirements with their association equivalent', () => {
    render(<MollieDocumentChecklist />);

    expect(screen.getByText('intro')).toBeInTheDocument();
    expect(screen.getByText('items.registration.mollieLabel')).toBeInTheDocument();
    expect(screen.getByText('items.registration.meaning')).toBeInTheDocument();
    expect(screen.getByText('items.identity.mollieLabel')).toBeInTheDocument();
    expect(screen.getByText('items.identity.meaning')).toBeInTheDocument();
    expect(screen.getByText('items.bank.mollieLabel')).toBeInTheDocument();
    expect(screen.getByText('items.bank.meaning')).toBeInTheDocument();
    expect(screen.getByText('note')).toBeInTheDocument();
  });

  it('deep-links the registry lookup when a SIREN is known', () => {
    render(<MollieDocumentChecklist siren="775672272" />);

    const link = screen.getByRole('link', { name: 'items.registration.link' });
    expect(link).toHaveAttribute(
      'href',
      'https://annuaire-entreprises.data.gouv.fr/rechercher?terme=775672272',
    );
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('falls back to the RNA when the association has no SIREN', () => {
    render(<MollieDocumentChecklist siren={null} identifier="W751004076" />);

    expect(screen.getByRole('link', { name: 'items.registration.link' })).toHaveAttribute(
      'href',
      'https://annuaire-entreprises.data.gouv.fr/rechercher?terme=W751004076',
    );
  });

  it('prefers the SIREN over the RNA, which several entities can share', () => {
    render(<MollieDocumentChecklist siren="775672272" identifier="W751004076" />);

    expect(screen.getByRole('link', { name: 'items.registration.link' })).toHaveAttribute(
      'href',
      'https://annuaire-entreprises.data.gouv.fr/rechercher?terme=775672272',
    );
  });

  it('uses a legacy SIREN held in the identifier when the SIREN column is empty', () => {
    render(<MollieDocumentChecklist siren={null} identifier="775672272" />);

    expect(screen.getByRole('link', { name: 'items.registration.link' })).toHaveAttribute(
      'href',
      'https://annuaire-entreprises.data.gouv.fr/rechercher?terme=775672272',
    );
  });

  it('omits the link when the identifier matches neither format, rather than linking to nothing', () => {
    render(<MollieDocumentChecklist siren={null} identifier="NOT-AN-ID" />);

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('omits the registry link when nothing identifies the association', () => {
    render(<MollieDocumentChecklist siren={null} identifier={null} />);

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
