import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { CampaignMilestonesTab } from '../CampaignMilestonesTab';
import type { CampaignDto } from '@/types/campaign';

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/hooks/campaign/useMilestones');

import { useMilestones } from '@/hooks/campaign/useMilestones';
const mockUseMilestones = useMilestones as ReturnType<typeof vi.fn>;

// ── Fixtures ──────────────────────────────────────────────────────────────────

const baseCampaign: CampaignDto = {
  id: 'camp-1',
  name: 'Test Campaign',
  emoji: '🌍',
  description: null,
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

const milestoneBase = {
  id: 'ms-1',
  emoji: '🎯',
  title: 'Premier palier',
  description: 'Acheter du matériel',
  transparencyCommitment: 'Photos à J+30',
  targetAmount: 5000,
  status: 'LOCKED' as const,
  sortOrder: 0,
  reachedAt: null,
  createdAt: '2024-01-01T00:00:00Z',
};

const mockHookBase = {
  isSaving: false,
  addNewMilestone: vi.fn().mockResolvedValue(null),
  updateExistingMilestone: vi.fn().mockResolvedValue(undefined),
  removeExistingMilestone: vi.fn().mockResolvedValue(undefined),
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('CampaignMilestonesTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseMilestones.mockReturnValue(mockHookBase);
  });

  it('shows empty state when no milestones', () => {
    render(<CampaignMilestonesTab campaign={baseCampaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByText('editor.milestones.emptyTitle')).toBeInTheDocument();
    expect(screen.getByText('editor.milestones.emptyCreate')).toBeInTheDocument();
  });

  it('calls addNewMilestone when empty state create button clicked', async () => {
    render(<CampaignMilestonesTab campaign={baseCampaign} onMilestonesChanged={vi.fn()} />);
    fireEvent.click(screen.getByText('editor.milestones.emptyCreate'));
    expect(mockHookBase.addNewMilestone).toHaveBeenCalledWith('camp-1', 0, 0);
  });

  it('shows milestone card with title and step number', () => {
    const campaign = { ...baseCampaign, milestones: [milestoneBase] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByDisplayValue('Premier palier')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('shows impact and proof fields with existing values', () => {
    const campaign = { ...baseCampaign, milestones: [milestoneBase] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByDisplayValue('Acheter du matériel')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Photos à J+30')).toBeInTheDocument();
  });

  it('shows REACHED badge for reached milestone', () => {
    const campaign = { ...baseCampaign, milestones: [{ ...milestoneBase, status: 'REACHED' as const }] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByText(/editor\.milestones\.reached/)).toBeInTheDocument();
  });

  it('shows CURRENT badge for current milestone', () => {
    const campaign = { ...baseCampaign, milestones: [{ ...milestoneBase, status: 'CURRENT' as const }] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByText(/editor\.milestones\.current/)).toBeInTheDocument();
  });

  it('keeps fields enabled and hides lock pill for a LOCKED milestone', () => {
    const campaign = { ...baseCampaign, milestones: [milestoneBase] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByDisplayValue('Premier palier')).not.toBeDisabled();
    expect(screen.getByDisplayValue('Acheter du matériel')).not.toBeDisabled();
    expect(screen.getByDisplayValue('Photos à J+30')).not.toBeDisabled();
    expect(screen.getByDisplayValue('5000')).not.toBeDisabled();
    expect(screen.queryByText('editor.milestones.fieldsLockedCurrent')).not.toBeInTheDocument();
    expect(screen.queryByText('editor.milestones.fieldsLockedReached')).not.toBeInTheDocument();
  });

  it('disables edit fields and shows the lock pill for a CURRENT milestone', () => {
    const campaign = { ...baseCampaign, milestones: [{ ...milestoneBase, status: 'CURRENT' as const }] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByDisplayValue('Premier palier')).toBeDisabled();
    expect(screen.getByDisplayValue('Acheter du matériel')).toBeDisabled();
    expect(screen.getByDisplayValue('Photos à J+30')).toBeDisabled();
    expect(screen.getByDisplayValue('5000')).toBeDisabled();
    expect(screen.getByText(/editor\.milestones\.fieldsLockedCurrent/)).toBeInTheDocument();
  });

  it('disables edit fields and shows the lock pill for a REACHED milestone', () => {
    const campaign = { ...baseCampaign, milestones: [{ ...milestoneBase, status: 'REACHED' as const }] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByDisplayValue('Premier palier')).toBeDisabled();
    expect(screen.getByDisplayValue('Acheter du matériel')).toBeDisabled();
    expect(screen.getByDisplayValue('Photos à J+30')).toBeDisabled();
    expect(screen.getByDisplayValue('5000')).toBeDisabled();
    expect(screen.getByText(/editor\.milestones\.fieldsLockedReached/)).toBeInTheDocument();
  });

  it('keeps the card itself visible and does not disable the delete button when locked', () => {
    const campaign = { ...baseCampaign, milestones: [{ ...milestoneBase, status: 'REACHED' as const }] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByDisplayValue('Premier palier')).toBeInTheDocument();
    expect(screen.getByText('✕')).not.toBeDisabled();
  });

  it('shows unreachable badge when targetAmount exceeds campaign goal', () => {
    const campaign = { ...baseCampaign, milestones: [{ ...milestoneBase, targetAmount: 99999 }] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByText(/editor\.milestones\.unreachable/)).toBeInTheDocument();
  });

  it('2-step delete: first click shows confirm, second click calls remove', async () => {
    const onChanged = vi.fn();
    const campaign = { ...baseCampaign, milestones: [milestoneBase] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={onChanged} />);

    const deleteBtn = screen.getByText('✕');
    fireEvent.click(deleteBtn);
    expect(screen.getByText('editor.milestones.deleteConfirm2')).toBeInTheDocument();
    expect(mockHookBase.removeExistingMilestone).not.toHaveBeenCalled();

    fireEvent.click(screen.getByText('editor.milestones.deleteConfirm2'));
    expect(mockHookBase.removeExistingMilestone).toHaveBeenCalledWith('camp-1', 'ms-1');
  });

  it('shows add button in header when milestones exist', () => {
    const campaign = { ...baseCampaign, milestones: [milestoneBase] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    expect(screen.getByText(/editor\.milestones\.add/)).toBeInTheDocument();
  });

  it('sorts milestones by sortOrder', () => {
    const ms2 = { ...milestoneBase, id: 'ms-2', title: 'Second palier', sortOrder: 1 };
    const ms1 = { ...milestoneBase, id: 'ms-1', title: 'Premier palier', sortOrder: 0 };
    const campaign = { ...baseCampaign, milestones: [ms2, ms1] };
    render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
    const inputs = screen.getAllByRole('textbox', { name: '' });
    const titleInputs = inputs.filter(el => (el as HTMLInputElement).value === 'Premier palier' || (el as HTMLInputElement).value === 'Second palier');
    expect((titleInputs[0] as HTMLInputElement).value).toBe('Premier palier');
    expect((titleInputs[1] as HTMLInputElement).value).toBe('Second palier');
  });

  describe('autosave', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('does not call updateExistingMilestone immediately on title change', () => {
      const campaign = { ...baseCampaign, milestones: [milestoneBase] };
      render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
      fireEvent.change(screen.getByDisplayValue('Premier palier'), { target: { value: 'Nouveau titre' } });
      expect(mockHookBase.updateExistingMilestone).not.toHaveBeenCalled();
    });

    it('merges title and impact edits made within the debounce window into a single update', () => {
      const campaign = { ...baseCampaign, milestones: [milestoneBase] };
      render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
      fireEvent.change(screen.getByDisplayValue('Premier palier'), { target: { value: 'Nouveau titre' } });
      act(() => { vi.advanceTimersByTime(300); });
      fireEvent.change(screen.getByDisplayValue('Acheter du matériel'), { target: { value: 'Nouvel impact' } });

      act(() => { vi.advanceTimersByTime(800); });

      expect(mockHookBase.updateExistingMilestone).toHaveBeenCalledTimes(1);
      expect(mockHookBase.updateExistingMilestone).toHaveBeenCalledWith('camp-1', 'ms-1', {
        title: 'Nouveau titre',
        description: 'Nouvel impact',
      });
    });

    it('flushes a pending edit on unmount (e.g. switching tabs before the debounce fires)', () => {
      const campaign = { ...baseCampaign, milestones: [milestoneBase] };
      const { unmount } = render(<CampaignMilestonesTab campaign={campaign} onMilestonesChanged={vi.fn()} />);
      fireEvent.change(screen.getByDisplayValue('Premier palier'), { target: { value: 'Nouveau titre' } });

      unmount();

      expect(mockHookBase.updateExistingMilestone).toHaveBeenCalledWith('camp-1', 'ms-1', { title: 'Nouveau titre' });
    });
  });
});
