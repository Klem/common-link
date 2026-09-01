import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DashboardShell } from '../DashboardShell';

const mockReplace = vi.fn();
let mockSearchParamsValue = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
  usePathname: () => '/fr/dashboard/donor',
  useSearchParams: () => mockSearchParamsValue,
}));

vi.mock('next-intl', () => ({
  useLocale: () => 'fr',
}));

let mockUser: { id: string; role: string; provider: string } = {
  id: 'user-1',
  role: 'DONOR',
  provider: 'EMAIL',
};

vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({ user: mockUser, isAuthenticated: true, isLoading: false }),
}));

vi.mock('../Sidebar', () => ({ Sidebar: () => <div>sidebar</div> }));
vi.mock('../AssociationStatusSync', () => ({ AssociationStatusSync: () => null }));

vi.mock('../SetPasswordModal', () => ({
  SetPasswordModal: ({ isOpen, variant }: { isOpen: boolean; variant?: string }) =>
    isOpen ? <div>{`modal:${variant}`}</div> : null,
}));

beforeEach(() => {
  vi.clearAllMocks();
  mockSearchParamsValue = new URLSearchParams();
  mockUser = { id: 'user-1', role: 'DONOR', provider: 'EMAIL' };
  window.localStorage.clear();
});

describe('DashboardShell', () => {
  it('does not show the password modal by default for an EMAIL-provider user', () => {
    render(<DashboardShell>content</DashboardShell>);
    expect(screen.queryByText(/^modal:/)).not.toBeInTheDocument();
  });

  it('force-opens the reset-variant modal when ?resetPassword=1 is present, and strips the param', () => {
    mockSearchParamsValue = new URLSearchParams('resetPassword=1');
    render(<DashboardShell>content</DashboardShell>);
    expect(screen.getByText('modal:reset')).toBeInTheDocument();
    expect(mockReplace).toHaveBeenCalledWith('/fr/dashboard/donor');
  });

  it('shows the add-variant modal for a passwordless-provider user until dismissed', () => {
    mockUser = { id: 'user-2', role: 'DONOR', provider: 'MAGIC_LINK' };
    render(<DashboardShell>content</DashboardShell>);
    expect(screen.getByText('modal:add')).toBeInTheDocument();
  });
});
