import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CampaignCard } from '../CampaignCard';
import type { CampaignSummaryDto } from '@/types/campaign';

// ── Mocks ─────────────────────────────────────────────────────────────────────

const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock('next-intl', () => ({
  useLocale: () => 'fr',
  useTranslations: () => (key: string) => key,
}));

// ── Fixtures ──────────────────────────────────────────────────────────────────

const baseCampaign: CampaignSummaryDto = {
  id: 'campaign-uuid-1',
  name: 'Hiver Solidaire 2025',
  emoji: '🌍',
  description: 'Aide hivernale pour les sans-abri',
  goal: 40000,
  raised: 10000,
  status: 'DRAFT' as const,
  startDate: null,
  endDate: null,
  milestoneCount: 2,
  createdAt: new Date().toISOString(),
};

describe('CampaignCard', () => {
  const onDelete = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Rendering ─────────────────────────────────────────────────────────────

  it('renders campaign name', () => {
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    expect(screen.getByText('Hiver Solidaire 2025')).toBeInTheDocument();
  });

  it('renders campaign emoji', () => {
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    expect(screen.getByText('🌍')).toBeInTheDocument();
  });

  it('renders DRAFT badge label', () => {
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    expect(screen.getByText(/campaigns\.badge\.draft/)).toBeInTheDocument();
  });

  it('renders LIVE badge label', () => {
    const liveCampaign = { ...baseCampaign, status: 'LIVE' as const };
    render(<CampaignCard campaign={liveCampaign} onDelete={onDelete} />);
    expect(screen.getByText(/campaigns\.badge\.live/)).toBeInTheDocument();
  });

  it('renders ENDED badge label', () => {
    const endedCampaign = { ...baseCampaign, status: 'ENDED' as const };
    render(<CampaignCard campaign={endedCampaign} onDelete={onDelete} />);
    expect(screen.getByText(/campaigns\.badge\.ended/)).toBeInTheDocument();
  });

  it('renders progress bar with correct width (25%)', () => {
    // raised=10000, goal=40000 → 25%
    const { container } = render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    const progressBar = container.querySelector('[style*="width: 25%"]');
    expect(progressBar).toBeInTheDocument();
  });

  it('shows draftNoBudget text when draft and goal is 0', () => {
    const nGoalCampaign = { ...baseCampaign, goal: 0, raised: 0 };
    render(<CampaignCard campaign={nGoalCampaign} onDelete={onDelete} />);
    expect(screen.getByText(/campaigns\.draftNoBudget/)).toBeInTheDocument();
  });

  it('caps progress bar at 100% when raised > goal', () => {
    const overCampaign = { ...baseCampaign, raised: 50000, goal: 40000 };
    const { container } = render(<CampaignCard campaign={overCampaign} onDelete={onDelete} />);
    const progressBar = container.querySelector('[style*="width: 100%"]');
    expect(progressBar).toBeInTheDocument();
  });

  // ── Action buttons ─────────────────────────────────────────────────────────

  it('DRAFT card shows Continuer button', () => {
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    expect(screen.getByText(/campaigns\.actionContinue/)).toBeInTheDocument();
  });

  it('LIVE card shows Gérer and Partager buttons', () => {
    const liveCampaign = { ...baseCampaign, status: 'LIVE' as const };
    render(<CampaignCard campaign={liveCampaign} onDelete={onDelete} />);
    expect(screen.getByText(/campaigns\.actionManage/)).toBeInTheDocument();
    expect(screen.getByText(/campaigns\.actionShare/)).toBeInTheDocument();
  });

  it('ENDED card shows Voir le rapport button', () => {
    const endedCampaign = { ...baseCampaign, status: 'ENDED' as const };
    render(<CampaignCard campaign={endedCampaign} onDelete={onDelete} />);
    expect(screen.getByText(/campaigns\.actionReport/)).toBeInTheDocument();
  });

  // ── Navigation ────────────────────────────────────────────────────────────

  it('clicking the card navigates to the campaign editor', () => {
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    fireEvent.click(screen.getByRole('button', { name: /Hiver Solidaire 2025/ }));
    expect(mockPush).toHaveBeenCalledWith(expect.stringContaining(baseCampaign.id));
  });

  it('pressing Enter on the card navigates', () => {
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    const card = screen.getByRole('button', { name: /Hiver Solidaire 2025/ });
    fireEvent.keyDown(card, { key: 'Enter' });
    expect(mockPush).toHaveBeenCalled();
  });

  // ── Delete button ─────────────────────────────────────────────────────────

  it('delete button calls onDelete with campaign id after confirm', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    fireEvent.click(screen.getByLabelText('campaigns.delete'));
    expect(onDelete).toHaveBeenCalledWith('campaign-uuid-1');
  });

  it('delete button does not call onDelete when confirm is cancelled', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    fireEvent.click(screen.getByLabelText('campaigns.delete'));
    expect(onDelete).not.toHaveBeenCalled();
  });

  it('delete button click does not trigger card navigation', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<CampaignCard campaign={baseCampaign} onDelete={onDelete} />);
    fireEvent.click(screen.getByLabelText('campaigns.delete'));
    expect(mockPush).not.toHaveBeenCalled();
  });
});
