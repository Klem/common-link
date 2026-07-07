import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Sidebar } from '../Sidebar';
import type { UserDto } from '@/types/auth';
import { UserRole, AuthProvider } from '@/types/auth';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'fr',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/fr/dashboard/donor',
}));

vi.mock('next/link', () => ({
  default: ({ href, children, className }: { href: string; children: React.ReactNode; className?: string }) => (
    <a href={href} className={className}>{children}</a>
  ),
}));

vi.mock('@/stores/authStore', () => ({
  useAuthStore: (selector: (s: { logout: () => void }) => unknown) =>
    selector({ logout: vi.fn() }),
}));

const donorUser: UserDto = {
  id: '1',
  email: 'donor@test.com',
  role: UserRole.DONOR,
  displayName: 'Alice Dupont',
  avatarUrl: null,
  provider: AuthProvider.EMAIL,
  emailVerified: true,
  createdAt: '2025-01-01T00:00:00Z',
};

const associationUser: UserDto = {
  ...donorUser,
  id: '2',
  email: 'asso@test.com',
  role: UserRole.ASSOCIATION,
  displayName: 'ONG Lumière',
};

describe('Sidebar', () => {
  it('renders the CommonLink logo', () => {
    render(<Sidebar user={donorUser} currentPath="/dashboard/donor" />);
    expect(screen.getByText('Common')).toBeInTheDocument();
    expect(screen.getByText('Link')).toBeInTheDocument();
  });

  it('shows donor nav items when user.role is DONOR', () => {
    render(<Sidebar user={donorUser} currentPath="/dashboard/donor" />);
    expect(screen.getByText('nav.overview')).toBeInTheDocument();
    expect(screen.getByText('nav.profile')).toBeInTheDocument();
    expect(screen.getByText('nav.donations')).toBeInTheDocument();
    expect(screen.queryByText('nav.settings')).not.toBeInTheDocument();
  });

  it('shows association nav groups when user.role is ASSOCIATION', () => {
    render(<Sidebar user={associationUser} currentPath="/dashboard/association" />);
    expect(screen.getByText('nav.overview')).toBeInTheDocument();
    expect(screen.getByText('nav.campaigns')).toBeInTheDocument();
    expect(screen.getByText('nav.reporting')).toBeInTheDocument();
    expect(screen.getByText('nav.payees')).toBeInTheDocument();
    expect(screen.getByText('nav.settings')).toBeInTheDocument();
    expect(screen.queryByText('nav.donations')).not.toBeInTheDocument();
  });

  it('renders snav-label group headers for association', () => {
    render(<Sidebar user={associationUser} currentPath="/dashboard/association" />);
    expect(screen.getByText('nav.group.principal')).toBeInTheDocument();
    expect(screen.getByText('nav.group.gestion')).toBeInTheDocument();
    expect(screen.getByText('nav.group.settings')).toBeInTheDocument();
  });

  it('highlights active nav item with active class', () => {
    render(<Sidebar user={donorUser} currentPath="/dashboard/donor" />);
    const overviewLink = screen.getByRole('link', { name: /nav\.overview/ });
    expect(overviewLink).toHaveClass('active');
  });

  it('renders acc-mini pill for association users', () => {
    render(<Sidebar user={associationUser} currentPath="/dashboard/association" />);
    expect(document.querySelector('.acc-mini')).toBeInTheDocument();
  });

  it('does not render acc-mini pill for donor users', () => {
    render(<Sidebar user={donorUser} currentPath="/dashboard/donor" />);
    expect(document.querySelector('.acc-mini')).not.toBeInTheDocument();
  });
});
