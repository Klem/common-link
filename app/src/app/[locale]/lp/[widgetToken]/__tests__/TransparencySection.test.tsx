import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TransparencySection } from '../TransparencySection';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

// jsdom has no IntersectionObserver — TransparencySection only uses it to trigger the bar-fill
// animation, irrelevant to what's rendered.
class StubIntersectionObserver {
  observe() {}
  disconnect() {}
}
vi.stubGlobal('IntersectionObserver', StubIntersectionObserver);

describe('TransparencySection — zero-percent budget items', () => {
  it('does not render a budget line rounding to 0%', () => {
    render(
      <TransparencySection
        budget={[
          { label: 'Achats', amount: 999, percentage: 99 },
          { label: 'Frais annexes', amount: 1, percentage: 0 },
        ]}
        milestones={[]}
      />
    );

    expect(screen.getByText('Achats')).toBeInTheDocument();
    expect(screen.queryByText('Frais annexes')).not.toBeInTheDocument();
  });

  it('omits the budget list entirely when every item rounds to 0%', () => {
    render(
      <TransparencySection
        budget={[{ label: 'Frais annexes', amount: 1, percentage: 0 }]}
        milestones={[]}
      />
    );

    expect(screen.queryByText('Frais annexes')).not.toBeInTheDocument();
  });
});
