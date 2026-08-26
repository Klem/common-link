import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProjectSection } from '../ProjectSection';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, unknown>) => {
    if (params) return key + ':' + JSON.stringify(params);
    return key;
  },
  useLocale: () => 'fr',
}));

const formatGoal = (amount: number, currency: string) =>
  new Intl.NumberFormat('fr', { style: 'currency', currency, maximumFractionDigits: 0 }).format(amount);

const formatDate = (iso: string) =>
  new Intl.DateTimeFormat('fr', { dateStyle: 'long' }).format(new Date(`${iso}T00:00:00`));

describe('ProjectSection — ACPR public-collection notice', () => {
  it('renders the formatted fundraising goal', () => {
    render(
      <ProjectSection
        campaignName="Hiver Solidaire"
        campaignDescription="Description"
        campaignImpactGoals={null}
        goal={40000}
        currency="EUR"
        startDate={null}
        endDate={null}
      />
    );

    const normalize = (s: string) => s.replace(/\s/g, ' ');
    expect(screen.getByText((text) => normalize(text) === normalize(formatGoal(40000, 'EUR')))).toBeInTheDocument();
  });

  it('renders the full calendrier range when both dates are set', () => {
    render(
      <ProjectSection
        campaignName="Hiver Solidaire"
        campaignDescription="Description"
        campaignImpactGoals={null}
        goal={40000}
        currency="EUR"
        startDate="2026-01-01"
        endDate="2026-12-31"
      />
    );

    const expected = JSON.stringify({ start: formatDate('2026-01-01'), end: formatDate('2026-12-31') });
    expect(screen.getByText(`project.calendarFull:${expected}`)).toBeInTheDocument();
  });

  it('renders a from-only calendrier line when only startDate is set', () => {
    render(
      <ProjectSection
        campaignName="Hiver Solidaire"
        campaignDescription="Description"
        campaignImpactGoals={null}
        goal={40000}
        currency="EUR"
        startDate="2026-01-01"
        endDate={null}
      />
    );

    const expected = JSON.stringify({ start: formatDate('2026-01-01') });
    expect(screen.getByText(`project.calendarFrom:${expected}`)).toBeInTheDocument();
  });

  it('omits the calendrier row entirely when neither date is set', () => {
    render(
      <ProjectSection
        campaignName="Hiver Solidaire"
        campaignDescription="Description"
        campaignImpactGoals={null}
        goal={40000}
        currency="EUR"
        startDate={null}
        endDate={null}
      />
    );

    expect(screen.queryByText('project.calendarLabel')).not.toBeInTheDocument();
  });

  it('renders the résultat attendu (impact goals) block when present', () => {
    render(
      <ProjectSection
        campaignName="Hiver Solidaire"
        campaignDescription="Description"
        campaignImpactGoals="200 repas servis chaque semaine"
        goal={40000}
        currency="EUR"
        startDate={null}
        endDate={null}
      />
    );

    expect(screen.getByText('200 repas servis chaque semaine')).toBeInTheDocument();
  });
});
