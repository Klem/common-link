import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PriorDecisionsBanner } from '../PriorDecisionsBanner';
import type { PriorDecisionDto } from '@/types/compliance';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

const priorFalsePositive: PriorDecisionDto = {
  alertId: 'a1000000-0000-0000-0000-000000000001',
  origin: 'FREEZE_HIT_ONBOARDING',
  decision: 'FALSE_POSITIVE',
  decisionRationale: 'Association loi 1901, RNA actif, sans lien avec TECHNOLAB.',
  createdAt: '2026-08-01T09:00:00Z',
};

describe('PriorDecisionsBanner', () => {
  it('renders nothing when the subject has never been ruled on', () => {
    const { container } = render(<PriorDecisionsBanner priorDecisions={[]} locale="fr" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('surfaces the previous ruling and its rationale', () => {
    render(<PriorDecisionsBanner priorDecisions={[priorFalsePositive]} locale="fr" />);
    expect(screen.getByText('detail.decision.FALSE_POSITIVE')).toBeInTheDocument();
    expect(
      screen.getByText('Association loi 1901, RNA actif, sans lien avec TECHNOLAB.'),
    ).toBeInTheDocument();
  });

  // The banner must read as context, never as a resolution: closure is irreversible and each
  // new correspondence raises a fresh alert that has to be examined on its own merits.
  it('states that the ruling is informative and does not settle the current alert', () => {
    render(<PriorDecisionsBanner priorDecisions={[priorFalsePositive]} locale="fr" />);
    expect(screen.getByText('priorDecisions.note')).toBeInTheDocument();
  });

  it('lists every prior ruling', () => {
    render(
      <PriorDecisionsBanner
        priorDecisions={[
          priorFalsePositive,
          { ...priorFalsePositive, alertId: 'a1000000-0000-0000-0000-000000000002', decision: 'LEGITIMATE' },
        ]}
        locale="fr"
      />,
    );
    expect(screen.getByText('detail.decision.FALSE_POSITIVE')).toBeInTheDocument();
    expect(screen.getByText('detail.decision.LEGITIMATE')).toBeInTheDocument();
  });
});
