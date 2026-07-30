import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CampaignHero } from '../CampaignHero';
import type { CampaignDto } from '@/types/campaign';

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

// ── Fixtures ──────────────────────────────────────────────────────────────────

const baseCampaign: CampaignDto = {
  id: 'camp-1',
  name: 'Test Campaign',
  emoji: '🌍',
  description: 'Description initiale',
  goal: 10000,
  raised: 2000,
  status: 'DRAFT',
  startDate: null,
  endDate: null,
  category: null,
  reason: null,
  impactGoals: null,
  coverImage: null,
  milestones: [],
  budgetSections: [],
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

function renderHero(campaign: CampaignDto) {
  return render(
    <CampaignHero
      campaign={campaign}
      onNameChange={vi.fn()}
      onEmojiChange={vi.fn()}
      onTabChange={vi.fn()}
    />,
  );
}

/** Classe CSS du pill de complétion portant la clé de libellé donnée. */
function pillClass(labelKey: string): string {
  return screen.getByText(labelKey, { exact: false }).closest('button')!.className;
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('CampaignHero completion pills', () => {
  it('marks the reason pill as a booster while `reason` is empty', () => {
    renderHero(baseCampaign);
    expect(pillClass('editor.completion.reason')).toContain('boost');
  });

  it('marks the reason pill as done once `reason` is filled', () => {
    renderHero({ ...baseCampaign, reason: 'Une urgence sanitaire' });
    expect(pillClass('editor.completion.reason')).toContain('done');
  });

  it('ignores a whitespace-only `reason`', () => {
    renderHero({ ...baseCampaign, reason: '   ' });
    expect(pillClass('editor.completion.reason')).toContain('boost');
  });

  it('marks the impact pill as done once `impactGoals` is filled', () => {
    renderHero({ ...baseCampaign, impactGoals: '500 repas distribués' });
    expect(pillClass('editor.completion.impact')).toContain('done');
  });

  it('keeps the impact pill as a booster while `impactGoals` is empty', () => {
    renderHero(baseCampaign);
    expect(pillClass('editor.completion.impact')).toContain('boost');
  });

  it('keeps the description pill missing below 10 characters', () => {
    renderHero({ ...baseCampaign, description: 'court' });
    expect(pillClass('editor.completion.description')).toContain('missing');
  });

  it('marks the description pill done from 10 characters', () => {
    renderHero({ ...baseCampaign, description: 'Description assez longue' });
    expect(pillClass('editor.completion.description')).toContain('done');
  });
});
