import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { CheckEmailScreen } from '../CheckEmailScreen';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const mockPost = vi.fn();
vi.mock('@/lib/api', () => ({
  default: { post: (...args: unknown[]) => mockPost(...args) },
}));

describe('CheckEmailScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  // ── Rendering ─────────────────────────────────────────────────────────────

  it('renders the title', () => {
    render(<CheckEmailScreen />);
    expect(screen.getByText('title')).toBeInTheDocument();
  });

  it('renders subtitle and validFor text', () => {
    render(<CheckEmailScreen />);
    expect(screen.getByText('subtitle')).toBeInTheDocument();
    expect(screen.getByText('validFor')).toBeInTheDocument();
  });

  it('renders the back-to-login link', () => {
    render(<CheckEmailScreen />);
    const link = screen.getByRole('link', { name: 'backToLogin' });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/fr/login');
  });

  it('renders the resend button', () => {
    render(<CheckEmailScreen />);
    expect(screen.getByRole('button', { name: 'resend' })).toBeInTheDocument();
  });

  // ── Resend button state ───────────────────────────────────────────────────

  it('resend button is disabled when no pending email in sessionStorage', () => {
    render(<CheckEmailScreen />);
    expect(screen.getByRole('button', { name: 'resend' })).toBeDisabled();
  });

  it('resend button is enabled when cl-pending-email is in sessionStorage', async () => {
    sessionStorage.setItem('cl-pending-email', 'user@example.com');
    render(<CheckEmailScreen />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'resend' })).not.toBeDisabled();
    });
  });

  // ── Resend interaction ────────────────────────────────────────────────────

  it('shows resending label while the request is in flight', async () => {
    sessionStorage.setItem('cl-pending-email', 'user@example.com');
    mockPost.mockReturnValue(new Promise(() => {})); // never resolves

    render(<CheckEmailScreen />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'resend' })).not.toBeDisabled();
    });

    fireEvent.click(screen.getByRole('button', { name: 'resend' }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'resending' })).toBeInTheDocument();
    });
  });

  it('shows resent confirmation after a successful resend', async () => {
    sessionStorage.setItem('cl-pending-email', 'user@example.com');
    mockPost.mockResolvedValue({});

    render(<CheckEmailScreen />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'resend' })).not.toBeDisabled();
    });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'resend' }));
    });

    await waitFor(() => {
      expect(screen.getByText('resent')).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: 'resend' })).not.toBeInTheDocument();
  });

  it('posts to resend-verification with the stored email', async () => {
    sessionStorage.setItem('cl-pending-email', 'user@example.com');
    mockPost.mockResolvedValue({});

    render(<CheckEmailScreen />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'resend' })).not.toBeDisabled();
    });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'resend' }));
    });

    expect(mockPost).toHaveBeenCalledWith('/api/auth/resend-verification', {
      email: 'user@example.com',
    });
  });

  it('still shows resent even when the API returns a 429 rate limit', async () => {
    sessionStorage.setItem('cl-pending-email', 'user@example.com');
    const rateLimitError = Object.assign(new Error('rate limited'), {
      isAxiosError: true,
      response: { status: 429 },
    });
    mockPost.mockRejectedValue(rateLimitError);

    render(<CheckEmailScreen />);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'resend' })).not.toBeDisabled();
    });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'resend' }));
    });

    await waitFor(() => {
      expect(screen.getByText('resent')).toBeInTheDocument();
    });
  });
});
