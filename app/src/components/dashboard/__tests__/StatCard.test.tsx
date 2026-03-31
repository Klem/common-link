import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatCard } from '../StatCard';

describe('StatCard', () => {
  it('renders icon, label, and value', () => {
    render(<StatCard icon="💰" label="Total donné" value="0 €" />);
    expect(screen.getByText('💰')).toBeInTheDocument();
    expect(screen.getByText('Total donné')).toBeInTheDocument();
    expect(screen.getByText('0 €')).toBeInTheDocument();
  });

  it('shows trend chip when trend prop is provided', () => {
    render(<StatCard icon="📊" label="Score" value={42} trend={{ value: '+5%', direction: 'up' }} />);
    expect(screen.getByText('+5%')).toBeInTheDocument();
  });

  it('does not show trend chip when trend is undefined', () => {
    render(<StatCard icon="📊" label="Score" value={42} />);
    expect(screen.queryByText(/[+-]\d/)).not.toBeInTheDocument();
  });
});
