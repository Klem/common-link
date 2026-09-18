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

  it('adds the stakeholder and activity requirements when the association holds a SIREN', () => {
    render(<MollieDocumentChecklist siren="775672272" />);

    expect(screen.getByText('items.stakeholders.mollieLabel')).toBeInTheDocument();
    expect(screen.getByText('items.stakeholders.meaning')).toBeInTheDocument();
    expect(screen.getByText('items.activity.mollieLabel')).toBeInTheDocument();
    expect(screen.getByText('items.activity.meaning')).toBeInTheDocument();
    // Listed after the registration item and before the personal ones, as Mollie orders them.
    const labels = screen.getAllByText(/^items\..*\.mollieLabel$/).map((n) => n.textContent);
    expect(labels).toEqual([
      'items.registration.mollieLabel',
      'items.stakeholders.mollieLabel',
      'items.activity.mollieLabel',
      'items.identity.mollieLabel',
      'items.bank.mollieLabel',
    ]);
  });

  it('keeps the three-item list when the association has no SIREN', () => {
    render(<MollieDocumentChecklist siren={null} identifier="W751004076" />);

    expect(screen.queryByText('items.stakeholders.mollieLabel')).not.toBeInTheDocument();
    expect(screen.queryByText('items.activity.mollieLabel')).not.toBeInTheDocument();
  });

  it('does not add them for a SIREN-shaped identifier, which the backend never sends to Mollie', () => {
    // createClientLink reads AssociationProfile.siren and nothing else, so a legacy SIREN sitting
    // in `identifier` produces no registrationNumber and no commercial-registry match.
    render(<MollieDocumentChecklist siren={null} identifier="775672272" />);

    expect(screen.queryByText('items.stakeholders.mollieLabel')).not.toBeInTheDocument();
    expect(screen.queryByText('items.activity.mollieLabel')).not.toBeInTheDocument();
  });

  it('ignores a blank SIREN rather than treating it as one', () => {
    render(<MollieDocumentChecklist siren="   " identifier="W751004076" />);

    expect(screen.queryByText('items.stakeholders.mollieLabel')).not.toBeInTheDocument();
  });
});
