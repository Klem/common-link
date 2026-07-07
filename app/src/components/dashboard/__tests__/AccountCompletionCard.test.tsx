import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AccountCompletionCard } from '../AccountCompletionCard';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, unknown>) => {
    if (params) return key + ':' + JSON.stringify(params);
    return key;
  },
  useLocale: () => 'fr',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

const mockSessionStorage: Record<string, string> = {};
beforeEach(() => {
  Object.defineProperty(window, 'sessionStorage', {
    value: {
      getItem: (k: string) => mockSessionStorage[k] ?? null,
      setItem: (k: string, v: string) => { mockSessionStorage[k] = v; },
    },
    writable: true,
  });
  delete mockSessionStorage['cl-acc-card-dismissed'];
});

describe('AccountCompletionCard', () => {
  it('renders both checks when 0/2 done', () => {
    render(<AccountCompletionCard verified={false} bankConnected={false} />);
    expect(screen.getByText('title')).toBeInTheDocument();
    expect(screen.getAllByText('checks.kyc.title')).toHaveLength(1);
    expect(screen.getAllByText('checks.bank.title')).toHaveLength(1);
  });

  it('marks KYC done when verified=true', () => {
    render(<AccountCompletionCard verified={true} bankConnected={false} />);
    const doneItems = document.querySelectorAll('.acc-check.done');
    expect(doneItems).toHaveLength(1);
    const pendingItems = document.querySelectorAll('.acc-check.pending');
    expect(pendingItems).toHaveLength(1);
  });

  it('renders nothing when both are done (2/2)', () => {
    const { container } = render(
      <AccountCompletionCard verified={true} bankConnected={true} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('calls sessionStorage and hides on dismiss', () => {
    render(<AccountCompletionCard verified={false} bankConnected={false} />);
    const closeBtn = screen.getByTitle('dismiss');
    fireEvent.click(closeBtn);
    expect(mockSessionStorage['cl-acc-card-dismissed']).toBe('1');
    expect(screen.queryByText('title')).not.toBeInTheDocument();
  });
});
