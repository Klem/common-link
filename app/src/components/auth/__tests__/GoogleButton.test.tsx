import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { GoogleButton } from '../GoogleButton';

vi.mock('@react-oauth/google', () => ({
  GoogleLogin: ({ onSuccess }: { onSuccess: (r: { credential: string }) => void }) => (
    <button role="button" onClick={() => onSuccess({ credential: 'mock-token' })}>
      GoogleInner
    </button>
  ),
}));

describe('GoogleButton', () => {
  it('renders the custom Google button', () => {
    render(<GoogleButton onSuccess={vi.fn()} />);
    // Custom button rendered with aria-label
    expect(screen.getByRole('button', { name: /continuer avec google/i })).toBeInTheDocument();
  });

  it('renders with a custom label', () => {
    render(<GoogleButton onSuccess={vi.fn()} label="S'inscrire avec Google" />);
    expect(screen.getByText("S'inscrire avec Google")).toBeInTheDocument();
  });

  it('is disabled when loading', () => {
    render(<GoogleButton onSuccess={vi.fn()} loading />);
    expect(screen.getByRole('button', { name: /continuer avec google/i })).toBeDisabled();
  });

  it('calls onSuccess with idToken when clicked', () => {
    const onSuccess = vi.fn();
    render(<GoogleButton onSuccess={onSuccess} />);
    fireEvent.click(screen.getByRole('button', { name: /continuer avec google/i }));
    expect(onSuccess).toHaveBeenCalledWith('mock-token');
  });
});
