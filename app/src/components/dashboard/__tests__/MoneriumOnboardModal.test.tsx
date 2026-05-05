import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import MoneriumOnboardModal from '../MoneriumOnboardModal';
import { MoneriumPopupMessage } from '@/types/monerium';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const mockAddToast = vi.fn();
vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: mockAddToast }),
}));

vi.mock('@/lib/api/monerium', () => ({
  getMoneriumAuthUrl: vi.fn(),
}));

import { getMoneriumAuthUrl } from '@/lib/api/monerium';
const mockGetMoneriumAuthUrl = getMoneriumAuthUrl as ReturnType<typeof vi.fn>;

const mockPopup = { close: vi.fn() } as unknown as Window;

describe('MoneriumOnboardModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, 'open').mockReturnValue(mockPopup);
  });

  // ── visibility ────────────────────────────────────────────────────────────

  it('renders nothing when isOpen is false', () => {
    const { container } = render(
      <MoneriumOnboardModal isOpen={false} onClose={vi.fn()} onConnected={vi.fn()} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders title, description, and action buttons when open', () => {
    render(<MoneriumOnboardModal isOpen={true} onClose={vi.fn()} onConnected={vi.fn()} />);

    expect(screen.getByText('profile.monerium.title')).toBeInTheDocument();
    expect(screen.getByText('profile.monerium.description')).toBeInTheDocument();
    expect(screen.getByText('profile.monerium.cancel')).toBeInTheDocument();
    expect(screen.getByText('profile.monerium.connect')).toBeInTheDocument();
  });

  // ── dismissal ─────────────────────────────────────────────────────────────

  it('cancel button calls onClose', () => {
    const onClose = vi.fn();
    render(<MoneriumOnboardModal isOpen={true} onClose={onClose} onConnected={vi.fn()} />);

    fireEvent.click(screen.getByText('profile.monerium.cancel'));

    expect(onClose).toHaveBeenCalled();
  });

  it('backdrop click calls onClose', () => {
    const onClose = vi.fn();
    const { container } = render(
      <MoneriumOnboardModal isOpen={true} onClose={onClose} onConnected={vi.fn()} />,
    );

    fireEvent.click(container.firstChild as Element);

    expect(onClose).toHaveBeenCalled();
  });

  it('clicking the inner content panel does not dismiss the modal', () => {
    const onClose = vi.fn();
    render(<MoneriumOnboardModal isOpen={true} onClose={onClose} onConnected={vi.fn()} />);

    // Click the title text (inside the content panel) — stopPropagation prevents onClose
    fireEvent.click(screen.getByText('profile.monerium.title'));

    expect(onClose).not.toHaveBeenCalled();
  });

  // ── connect flow ──────────────────────────────────────────────────────────

  it('connect button calls getMoneriumAuthUrl and opens popup', async () => {
    mockGetMoneriumAuthUrl.mockResolvedValue({ authUrl: 'https://sandbox.monerium.app/auth?test=1' });

    render(<MoneriumOnboardModal isOpen={true} onClose={vi.fn()} onConnected={vi.fn()} />);
    fireEvent.click(screen.getByText('profile.monerium.connect'));

    await waitFor(() => {
      expect(mockGetMoneriumAuthUrl).toHaveBeenCalledTimes(1);
      expect(window.open).toHaveBeenCalledWith(
        'https://sandbox.monerium.app/auth?test=1',
        'monerium-popup',
        expect.any(String),
      );
    });
  });

  it('shows loading spinner while popup is open', async () => {
    // Never resolves — simulates a slow API or open popup
    mockGetMoneriumAuthUrl.mockReturnValue(new Promise(() => {}));

    render(<MoneriumOnboardModal isOpen={true} onClose={vi.fn()} onConnected={vi.fn()} />);

    act(() => {
      fireEvent.click(screen.getByText('profile.monerium.connect'));
    });

    await waitFor(() => {
      expect(screen.getByText('profile.monerium.waiting')).toBeInTheDocument();
    });
    // Action buttons should be replaced by the spinner
    expect(screen.queryByText('profile.monerium.connect')).not.toBeInTheDocument();
  });

  it('shows error toast and hides spinner when getMoneriumAuthUrl fails', async () => {
    mockGetMoneriumAuthUrl.mockRejectedValue(new Error('Network error'));

    render(<MoneriumOnboardModal isOpen={true} onClose={vi.fn()} onConnected={vi.fn()} />);
    fireEvent.click(screen.getByText('profile.monerium.connect'));

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('error', 'moneriumErrorFetch');
      expect(screen.getByText('profile.monerium.connect')).toBeInTheDocument();
    });
  });

  // ── postMessage handling ──────────────────────────────────────────────────

  it('CONNECTED message calls onConnected, onClose, and shows success toast', () => {
    const onConnected = vi.fn();
    const onClose = vi.fn();
    render(<MoneriumOnboardModal isOpen={true} onClose={onClose} onConnected={onConnected} />);

    act(() => {
      window.dispatchEvent(
        new MessageEvent('message', {
          origin: window.location.origin,
          data: { type: MoneriumPopupMessage.CONNECTED },
        }),
      );
    });

    expect(onConnected).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(mockAddToast).toHaveBeenCalledWith('success', 'moneriumConnected');
  });

  it('ERROR message shows error toast without calling onConnected', () => {
    const onConnected = vi.fn();
    render(<MoneriumOnboardModal isOpen={true} onClose={vi.fn()} onConnected={onConnected} />);

    act(() => {
      window.dispatchEvent(
        new MessageEvent('message', {
          origin: window.location.origin,
          data: { type: MoneriumPopupMessage.ERROR },
        }),
      );
    });

    expect(onConnected).not.toHaveBeenCalled();
    expect(mockAddToast).toHaveBeenCalledWith('error', 'moneriumError');
  });

  it('ignores postMessage from a different origin', () => {
    const onConnected = vi.fn();
    render(<MoneriumOnboardModal isOpen={true} onClose={vi.fn()} onConnected={onConnected} />);

    act(() => {
      window.dispatchEvent(
        new MessageEvent('message', {
          origin: 'https://evil.example.com',
          data: { type: MoneriumPopupMessage.CONNECTED },
        }),
      );
    });

    expect(onConnected).not.toHaveBeenCalled();
    expect(mockAddToast).not.toHaveBeenCalled();
  });

  it('does not listen for messages when isOpen is false', () => {
    const onConnected = vi.fn();
    render(<MoneriumOnboardModal isOpen={false} onClose={vi.fn()} onConnected={onConnected} />);

    act(() => {
      window.dispatchEvent(
        new MessageEvent('message', {
          origin: window.location.origin,
          data: { type: MoneriumPopupMessage.CONNECTED },
        }),
      );
    });

    expect(onConnected).not.toHaveBeenCalled();
  });
});
