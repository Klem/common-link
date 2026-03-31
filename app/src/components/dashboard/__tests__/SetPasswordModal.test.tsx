import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SetPasswordModal } from '../SetPasswordModal';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

vi.mock('@/lib/api', () => ({
  default: { patch: vi.fn() },
}));

vi.mock('@/components/auth/SetPasswordForm', () => ({
  SetPasswordForm: ({
    onSkip,
  }: {
    onSubmit: (password: string) => Promise<void>;
    onSkip: () => void;
    loading?: boolean;
  }) => (
    <div>
      <button onClick={onSkip}>setPassword.skip</button>
    </div>
  ),
}));

describe('SetPasswordModal', () => {
  it('renders nothing when isOpen is false', () => {
    const { container } = render(<SetPasswordModal isOpen={false} onClose={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders modal content when isOpen is true', () => {
    render(<SetPasswordModal isOpen={true} onClose={vi.fn()} />);
    expect(screen.getByText('setPassword.title')).toBeInTheDocument();
    expect(screen.getByText('setPassword.subtitle')).toBeInTheDocument();
  });

  it('calls onClose when skip is clicked', () => {
    const onClose = vi.fn();
    render(<SetPasswordModal isOpen={true} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /setPassword\.skip/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
