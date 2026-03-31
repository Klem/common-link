import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EmptyStateCard } from '../EmptyStateCard';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

describe('EmptyStateCard', () => {
  it('renders title and subtitle', () => {
    render(<EmptyStateCard title="Titre vide" subtitle="Sous-titre explicatif" />);
    expect(screen.getByText('Titre vide')).toBeInTheDocument();
    expect(screen.getByText('Sous-titre explicatif')).toBeInTheDocument();
  });

  it('renders CTA button when actionLabel and actionHref are provided', () => {
    render(
      <EmptyStateCard
        title="Titre"
        subtitle="Sous-titre"
        actionLabel="Commencer"
        actionHref="/campaigns"
      />,
    );
    const link = screen.getByRole('link', { name: 'Commencer' });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/campaigns');
  });

  it('does not render CTA when action props are absent', () => {
    render(<EmptyStateCard title="Titre" subtitle="Sous-titre" />);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
