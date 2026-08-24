import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { AuthProvider } from '../AuthProvider';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

let mockPathname = '/fr/dashboard/association';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

const mockHydrateFromStorage = vi.fn();
const mockSetState = vi.fn();
vi.mock('@/stores/authStore', () => ({
  useAuthStore: Object.assign(vi.fn(), {
    getState: () => ({ hydrateFromStorage: mockHydrateFromStorage }),
    setState: (...args: unknown[]) => mockSetState(...args),
  }),
}));

describe('AuthProvider', () => {
  beforeEach(() => {
    mockHydrateFromStorage.mockReset().mockReturnValue({ finally: (cb: () => void) => cb() });
    mockSetState.mockReset();
  });

  it('renders children immediately (server-side too) on the public landing page — no auth call', () => {
    mockPathname = '/fr/lp/clk_abc';

    render(
      <AuthProvider>
        <div>real landing content</div>
      </AuthProvider>,
    );

    // No `waitFor`/effect flush needed: this must be true on the very first render, since that
    // is what the server-rendered HTML contains too — the whole point of the fix.
    expect(screen.getByText('real landing content')).toBeInTheDocument();
    expect(screen.queryByText('loading')).not.toBeInTheDocument();
    expect(mockHydrateFromStorage).not.toHaveBeenCalled();
  });

  it('renders children immediately on embed routes — no auth call', () => {
    mockPathname = '/fr/embed/donate/clk_abc';

    render(
      <AuthProvider>
        <div>widget content</div>
      </AuthProvider>,
    );

    expect(screen.getByText('widget content')).toBeInTheDocument();
    expect(mockHydrateFromStorage).not.toHaveBeenCalled();
  });

  it('shows a loading fallback on protected routes until hydrateFromStorage resolves', async () => {
    mockPathname = '/fr/dashboard/association';
    let resolveHydrate!: () => void;
    mockHydrateFromStorage.mockReturnValue({
      finally: (cb: () => void) => {
        resolveHydrate = cb;
        return Promise.resolve();
      },
    });

    render(
      <AuthProvider>
        <div>dashboard content</div>
      </AuthProvider>,
    );

    expect(screen.getByText('loading')).toBeInTheDocument();
    expect(screen.queryByText('dashboard content')).not.toBeInTheDocument();
    expect(mockHydrateFromStorage).toHaveBeenCalledTimes(1);

    resolveHydrate();
    await waitFor(() => screen.getByText('dashboard content'));
  });
});
