import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import MollieOnboardModal from '../MollieOnboardModal';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/mollie-connect', () => ({
  getMollieAuthUrl: vi.fn(),
}));

vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

function renderModal(overrides: Partial<React.ComponentProps<typeof MollieOnboardModal>> = {}) {
  return render(
    <MollieOnboardModal
      isOpen
      onClose={vi.fn()}
      onConnected={vi.fn()}
      contactEmail="contact@asso.fr"
      contactName="Marie Dupont"
      {...overrides}
    />,
  );
}

describe('MollieOnboardModal', () => {
  it('shows the document checklist before the user starts the flow', () => {
    renderModal({ siren: '775672272' });

    expect(screen.getByText('intro')).toBeInTheDocument();
    expect(screen.getByText('items.registration.mollieLabel')).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'items.registration.link' }),
    ).toHaveAttribute('href', 'https://annuaire-entreprises.data.gouv.fr/rechercher?terme=775672272');
  });

  it('hides the checklist while contact details are missing, so the blocker stays visible', () => {
    renderModal({ contactEmail: null });

    expect(screen.queryByText('intro')).not.toBeInTheDocument();
    expect(screen.getByText('mollie.modal.missingContactEmail')).toBeInTheDocument();
  });

  it('renders nothing when closed', () => {
    const { container } = renderModal({ isOpen: false });

    expect(container).toBeEmptyDOMElement();
  });
});
