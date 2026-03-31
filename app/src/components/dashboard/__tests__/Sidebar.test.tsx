import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Sidebar } from '../Sidebar';
import type { UserDto } from '@/types/auth';

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
  role: 'DONOR',
  displayName: 'Alice Dupont',
  avatarUrl: null,
  provider: 'EMAIL',
  emailVerified: true,
  createdAt: '2025-01-01T00:00:00Z',
};

const associationUser: UserDto = {
  ...donorUser,
  id: '2',
  email: 'asso@test.com',
  role: 'ASSOCIATION',
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
    expect(screen.queryByText('nav.associationProfile')).not.toBeInTheDocument();
  });

  it('shows association nav items when user.role is ASSOCIATION', () => {
    render(<Sidebar user={associationUser} currentPath="/dashboard/association" />);
    expect(screen.getByText('nav.overview')).toBeInTheDocument();
    expect(screen.getByText('nav.associationProfile')).toBeInTheDocument();
    expect(screen.getByText('nav.donors')).toBeInTheDocument();
    expect(screen.queryByText('nav.donations')).not.toBeInTheDocument();
  });

  it('highlights active nav item based on currentPath', () => {
    render(<Sidebar user={donorUser} currentPath="/dashboard/donor" />);
    const overviewLink = screen.getByRole('link', { name: /nav\.overview/ });
    expect(overviewLink).toHaveClass('bg-green/10');
    expect(overviewLink).toHaveClass('text-green');
  });
});
