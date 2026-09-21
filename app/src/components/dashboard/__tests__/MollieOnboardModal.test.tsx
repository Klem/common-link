import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import MollieOnboardModal from '../MollieOnboardModal';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/mollie-connect', () => ({
  getMollieAuthUrl: vi.fn().mockResolvedValue({ authUrl: 'https://my.mollie.com/oauth2/authorize' }),
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

  it('swaps the checklist for the step-by-step guide once the Mollie window is open', async () => {
    vi.spyOn(window, 'open').mockReturnValue({ closed: false, close: vi.fn() } as unknown as Window);
    renderModal({ name: 'Fondation Lumiere' });

    fireEvent.click(screen.getByText('mollie.modal.connect'));

    // The wizard is being filled in right now, in the other window: guidance beats a bare spinner.
    await waitFor(() => expect(screen.getByText('title')).toBeInTheDocument());
    expect(screen.getByText('mollie.modal.waiting')).toBeInTheDocument();
    expect(screen.queryByText('items.registration.mollieLabel')).not.toBeInTheDocument();
  });

  it('renders nothing when closed', () => {
    const { container } = renderModal({ isOpen: false });

    expect(container).toBeEmptyDOMElement();
  });
});
