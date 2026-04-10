import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { VerifyEmailScreen } from '../VerifyEmailScreen';
import type { AuthResponseDto } from '@/types/auth';
import { UserRole, AuthProvider } from '@/types/auth';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockPost = vi.fn();
vi.mock('@/lib/api', () => ({
  default: { post: (...args: unknown[]) => mockPost(...args) },
}));

const mockSetAuth = vi.fn();
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({ setAuth: mockSetAuth }),
}));

function makeAuthResponse(role: typeof UserRole[keyof typeof UserRole] = UserRole.DONOR): AuthResponseDto {
  return {
    accessToken: 'access.token',
    refreshToken: 'refresh.token',
    user: {
      id: 'user-1',
      email: 'user@example.com',
      role,
      provider: AuthProvider.EMAIL,
      displayName: null,
      avatarUrl: null,
      emailVerified: true,
      createdAt: '2024-01-01T00:00:00Z',
    },
  };
}

describe('VerifyEmailScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    vi.useRealTimers();
  });

  // ── Initial states ────────────────────────────────────────────────────────

  it('shows verifying state initially when a token is provided', () => {
    mockPost.mockReturnValue(new Promise(() => {})); // never resolves
    render(<VerifyEmailScreen token="valid-token" />);
    expect(screen.getByText('verifying')).toBeInTheDocument();
  });

  it('shows failed state immediately when token is empty', async () => {
    render(<VerifyEmailScreen token="" />);
    await waitFor(() => {
      expect(screen.getByText('failed')).toBeInTheDocument();
    });
    expect(mockPost).not.toHaveBeenCalled();
  });

  // ── Success path ──────────────────────────────────────────────────────────

  it('shows success state after a valid token is verified', async () => {
    mockPost.mockResolvedValue({ data: makeAuthResponse() });
    render(<VerifyEmailScreen token="good-token" />);
    await waitFor(() => {
      expect(screen.getByText('success')).toBeInTheDocument();
    });
    expect(screen.getByText('successSubtitle')).toBeInTheDocument();
  });

  it('calls setAuth with the returned tokens and user', async () => {
    const response = makeAuthResponse(UserRole.DONOR);
    mockPost.mockResolvedValue({ data: response });
    render(<VerifyEmailScreen token="good-token" />);
    await waitFor(() => {
      expect(mockSetAuth).toHaveBeenCalledWith(
        response.accessToken,
        response.refreshToken,
        response.user,
      );
    });
  });

  it('clears cl-pending-email from sessionStorage on success', async () => {
    sessionStorage.setItem('cl-pending-email', 'user@example.com');
    mockPost.mockResolvedValue({ data: makeAuthResponse() });
    render(<VerifyEmailScreen token="good-token" />);
    await waitFor(() => {
      expect(screen.getByText('success')).toBeInTheDocument();
    });
    expect(sessionStorage.getItem('cl-pending-email')).toBeNull();
  });

  it('redirects to donor dashboard after success when role is DONOR', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockPost.mockResolvedValue({ data: makeAuthResponse(UserRole.DONOR) });
    render(<VerifyEmailScreen token="good-token" />);
    await waitFor(() => expect(screen.getByText('success')).toBeInTheDocument());

    await vi.runAllTimersAsync();

    expect(mockPush).toHaveBeenCalledWith('/fr/dashboard/donor');
  });

  it('redirects to association dashboard after success when role is ASSOCIATION', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockPost.mockResolvedValue({ data: makeAuthResponse(UserRole.ASSOCIATION) });
    render(<VerifyEmailScreen token="good-token" />);
    await waitFor(() => expect(screen.getByText('success')).toBeInTheDocument());

    await vi.runAllTimersAsync();

    expect(mockPush).toHaveBeenCalledWith('/fr/dashboard/association');
  });

  // ── Failure path ──────────────────────────────────────────────────────────

  it('shows failed state when the API returns 401 (invalid token)', async () => {
    const error = Object.assign(new Error('unauthorized'), {
      isAxiosError: true,
      response: { status: 401 },
    });
    mockPost.mockRejectedValue(error);
    render(<VerifyEmailScreen token="bad-token" />);
    await waitFor(() => {
      expect(screen.getByText('failed')).toBeInTheDocument();
    });
    expect(screen.getByText('failedSubtitle')).toBeInTheDocument();
  });

  it('failed state renders a link to request a new verification', async () => {
    mockPost.mockRejectedValue(new Error('network error'));
    render(<VerifyEmailScreen token="bad-token" />);
    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'requestNew' })).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: 'requestNew' })).toHaveAttribute(
      'href',
      '/fr/auth/check-email',
    );
  });

  it('does not redirect when verification fails', async () => {
    mockPost.mockRejectedValue(new Error('unauthorized'));
    render(<VerifyEmailScreen token="bad-token" />);
    await waitFor(() => {
      expect(screen.getByText('failed')).toBeInTheDocument();
    });
    expect(mockPush).not.toHaveBeenCalled();
  });
});
