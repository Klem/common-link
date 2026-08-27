import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import AssociationsPage from '../page';
import type { Page } from '@/types/payment';
import type { ComplianceAssociationSummaryDto } from '@/types/compliance';

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/lib/routes', () => ({
  ROUTES: {
    compliance: {
      associations: '/compliance/associations',
      associationDetail: (id: string) => `/compliance/associations/${id}`,
    },
  },
}));

vi.mock('@/components/compliance/complianceShared', () => ({
  ASSOCIATION_STATUS_BADGE_CLASS: {
    ACTIVE: 'badge badge-success',
    ALERT: 'badge badge-warning',
    SUSPENDED: 'badge badge-error',
  },
}));

const mockListAssociations = vi.fn();
vi.mock('@/lib/api/compliance', () => ({
  listAssociations: (...args: unknown[]) => mockListAssociations(...args),
}));

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makePage(items: Partial<ComplianceAssociationSummaryDto>[], total = 1): Page<ComplianceAssociationSummaryDto> {
  const content = items.map((item, i) => ({
    id: `id-${i}`,
    name: `Association ${i}`,
    identifier: 'W123456789',
    status: 'ACTIVE' as const,
    verificationStatus: 'VERIFIED' as const,
    riskLevel: 'STANDARD' as const,
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

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('AssociationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders rows returned by the API', async () => {
    mockListAssociations.mockResolvedValue(
      makePage([{ name: 'Les Restos du Cœur', identifier: 'W987654321' }]),
    );
    render(<AssociationsPage />);
    await waitFor(() => expect(screen.getByText('Les Restos du Cœur')).toBeInTheDocument());
    expect(screen.getByText('W987654321')).toBeInTheDocument();
  });

  it('shows empty state when no results', async () => {
    mockListAssociations.mockResolvedValue(makePage([]));
    render(<AssociationsPage />);
    await waitFor(() => expect(screen.getByText('associations.empty')).toBeInTheDocument());
  });

  it('shows error state when the API fails', async () => {
    mockListAssociations.mockRejectedValue(new Error('network'));
    render(<AssociationsPage />);
    await waitFor(() => expect(screen.getByText('associations.error')).toBeInTheDocument());
  });

  it('links each row to its association detail page', async () => {
    mockListAssociations.mockResolvedValue(makePage([{ id: 'assoc-42', name: 'Asso Alpha' }]));
    render(<AssociationsPage />);
    await waitFor(() => screen.getByText('Asso Alpha'));
    expect(screen.getByText('Asso Alpha').closest('a')).toHaveAttribute(
      'href',
      '/fr/compliance/associations/assoc-42',
    );
  });

  it('renders pagination controls when totalPages > 1', async () => {
    const page: Page<ComplianceAssociationSummaryDto> = {
      ...makePage([{ name: 'X' }], 50),
      totalPages: 3,
      last: false,
    };
    mockListAssociations.mockResolvedValue(page);
    render(<AssociationsPage />);
    await waitFor(() => screen.getByText('X'));
    const nextBtn = screen.getByText('›');
    expect(nextBtn).not.toBeDisabled();
  });
});
