import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { StatCard } from '../StatCard';

describe('StatCard', () => {
  it('renders icon, label, and value', () => {
    render(<StatCard icon="💰" label="Total donné" value="0 €" />);
    expect(screen.getByText('💰')).toBeInTheDocument();
    expect(screen.getByText('Total donné')).toBeInTheDocument();
    expect(screen.getByText('0 €')).toBeInTheDocument();
  });

  it('renders subLabel when provided', () => {
    render(<StatCard icon="🎯" label="Jalon" value="500 €" subLabel="Prochaine étape" />);
    expect(screen.getByText('Prochaine étape')).toBeInTheDocument();
  });

  it('calls onClick when clicked', () => {
    const onClick = vi.fn();
    render(<StatCard icon="📢" label="Campagnes" value={3} onClick={onClick} />);
    fireEvent.click(screen.getByText('Campagnes').closest('.st')!);
    expect(onClick).toHaveBeenCalledOnce();
  });
});
