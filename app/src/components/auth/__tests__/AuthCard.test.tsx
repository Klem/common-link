import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthCard } from '../AuthCard';

describe('AuthCard', () => {
  it('renders children', () => {
    render(<AuthCard><p>content</p></AuthCard>);
    expect(screen.getByText('content')).toBeInTheDocument();
  });

  it('accepts additional className', () => {
    const { container } = render(<AuthCard className="extra"><p>x</p></AuthCard>);
    expect(container.firstChild).toHaveClass('extra');
  });
});
