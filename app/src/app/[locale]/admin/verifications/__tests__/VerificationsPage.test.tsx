import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Suspense } from 'react';
import VerificationsPage from '../page';
import type { Page } from '@/types/payment';
import type { AdminVerificationSummaryDto } from '@/types/admin';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams('status=PENDING');

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => mockSearchParams,
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/lib/routes', () => ({
  ROUTES: {
    admin: {
      verifications: '/admin/verifications',
      verificationDetail: (id: string) => `/admin/verifications/${id}`,
    },
  },
}));

vi.mock('@/components/admin/adminShared', () => ({
  STATUS_BADGE_CLASS: {
    PENDING: 'badge badge-warning',
    VERIFIED: 'badge badge-success',
    REJECTED: 'badge badge-error',
    UNVERIFIED: 'badge badge-neutral',
  },
}));

const mockListVerifications = vi.fn();
vi.mock('@/lib/api/admin', () => ({
  listVerifications: (...args: unknown[]) => mockListVerifications(...args),
}));

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makePage(items: Partial<AdminVerificationSummaryDto>[], total = 1): Page<AdminVerificationSummaryDto> {
  const content = items.map((item, i) => ({
    associationId: `id-${i}`,
    name: `Association ${i}`,
    identifier: '123456789',
    status: 'PENDING' as const,
    submittedAt: '2026-07-01T00:00:00Z',
    docCount: 3,
    ...item,
  }));
  return {
    content,
    totalElements: total,
    totalPages: Math.ceil(total / 20),
    number: 0,
    size: 20,
    first: true,
    last: true,
  };
}

function renderPage() {
  return render(
    <Suspense>
      <VerificationsPage />
    </Suspense>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('VerificationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams('status=PENDING');
  });

  it('renders PENDING rows returned by the API', async () => {
    mockListVerifications.mockResolvedValue(
      makePage([{ name: 'Asso Alpha', identifier: 'W123456789' }]),
    );
    renderPage();
    await waitFor(() => expect(screen.getByText('Asso Alpha')).toBeInTheDocument());
    expect(screen.getByText('W123456789')).toBeInTheDocument();
  });

  it('shows empty state when no results', async () => {
    mockListVerifications.mockResolvedValue(makePage([]));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('verifications.empty')).toBeInTheDocument(),
    );
  });

  it('shows error state when API fails', async () => {
    mockListVerifications.mockRejectedValue(new Error('network'));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('verifications.error')).toBeInTheDocument(),
    );
  });

  it('clicking a tab pushes a new URL with the selected status', async () => {
    mockListVerifications.mockResolvedValue(makePage([]));
    renderPage();
    await waitFor(() => screen.getByText('verifications.empty'));

    fireEvent.click(screen.getByText('verifications.tabs.verified'));
    expect(mockPush).toHaveBeenCalledWith(expect.stringContaining('status=VERIFIED'));
  });

  it('renders pagination controls when totalPages > 1', async () => {
    const page: Page<AdminVerificationSummaryDto> = {
      ...makePage([{ name: 'X' }], 50),
      totalPages: 3,
      last: false,
    };
    mockListVerifications.mockResolvedValue(page);
    renderPage();
    await waitFor(() => screen.getByText('X'));
    // Next button should be enabled
    const nextBtn = screen.getByText('→');
    expect(nextBtn).not.toBeDisabled();
  });

  it('defaults to PENDING tab when no status param', async () => {
    mockSearchParams = new URLSearchParams();
    mockListVerifications.mockResolvedValue(makePage([]));
    renderPage();
    await waitFor(() => screen.getByText('verifications.empty'));
    expect(mockListVerifications).toHaveBeenCalledWith('PENDING', 0);
  });
});
