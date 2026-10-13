import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import MollieOnboardModal from '../MollieOnboardModal';

vi.mock('next-intl', () => {
  const t = Object.assign((key: string) => key, { rich: (key: string) => key });
  return { useTranslations: () => t };
});

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
  it('offers only "cancel" and "continue" before the flow starts, with no close cross', () => {
    const onClose = vi.fn();
    renderModal({ onClose });

    expect(screen.getByText('mollie.modal.description')).toBeInTheDocument();
    expect(screen.queryByLabelText('close')).not.toBeInTheDocument();
    expect(screen.queryByText('items.registration.mollieLabel')).not.toBeInTheDocument();

    fireEvent.click(document.querySelector('.modal-backdrop') as Element);
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByText('mollie.modal.cancel'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('shows the blocker while contact details are missing', () => {
    renderModal({ contactEmail: null });

    expect(screen.getByText('mollie.modal.missingContactEmail')).toBeInTheDocument();
  });

  it('swaps the confirmation for the step-by-step guide once the Mollie window is open', async () => {
    vi.spyOn(window, 'open').mockReturnValue({ closed: false, close: vi.fn() } as unknown as Window);
    renderModal({ name: 'Fondation Lumiere' });

    fireEvent.click(screen.getByText('mollie.modal.connect'));

    // The wizard is being filled in right now, in the other window: guidance beats a bare spinner.
    // Same 10-step translator as screen 3 of the bank tab.
    await waitFor(() => expect(screen.getByText('steps.account.title')).toBeInTheDocument());
    expect(screen.getByText('mollie.modal.waiting')).toBeInTheDocument();
    expect(screen.queryByText('items.registration.mollieLabel')).not.toBeInTheDocument();
  });

  it('keeps the Mollie popup and the modal on an error message, until the popup closes itself', async () => {
    vi.useFakeTimers();
    const popup = { closed: false, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(popup as unknown as Window);
    const onClose = vi.fn();
    const onPopupClosed = vi.fn();
    renderModal({ onClose, onPopupClosed });

    await act(async () => { fireEvent.click(screen.getByText('mollie.modal.connect')); });
    await act(async () => {
      window.dispatchEvent(new MessageEvent('message', { origin: window.location.origin, data: { type: 'MOLLIE_KYC_ERROR' } }));
    });

    // The popup page explains the failure and closes itself: the opener must not kill it early.
    expect(popup.close).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();

    popup.closed = true;
    await act(async () => { vi.advanceTimersByTime(600); });
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onPopupClosed).toHaveBeenCalledTimes(1);
    vi.useRealTimers();
  });

  it('renders nothing when closed', () => {
    const { container } = renderModal({ isOpen: false });

    expect(container).toBeEmptyDOMElement();
  });
});
