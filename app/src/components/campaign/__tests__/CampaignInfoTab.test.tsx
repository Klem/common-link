import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { CampaignInfoTab } from '../CampaignInfoTab';
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

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('CampaignInfoTab', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not autosave immediately when a field is edited', () => {
    const onSave = vi.fn();
    render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });
    expect(onSave).not.toHaveBeenCalled();
  });

  it('autosaves silently 800ms after a field edit', () => {
    const onSave = vi.fn();
    render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });

    act(() => { vi.advanceTimersByTime(800); });

    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave).toHaveBeenCalledWith({ name: 'Nouveau nom' }, true);
  });

  it('merges edits to several fields made within the debounce window into a single autosave', () => {
    const onSave = vi.fn();
    render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });
    act(() => { vi.advanceTimersByTime(300); });
    fireEvent.change(screen.getByDisplayValue('Description initiale'), { target: { value: 'Nouvelle description' } });

    act(() => { vi.advanceTimersByTime(800); });

    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave).toHaveBeenCalledWith({ name: 'Nouveau nom', description: 'Nouvelle description' }, true);
  });

  it('does not autosave while the date range is invalid', () => {
    const onSave = vi.fn();
    const campaign = { ...baseCampaign, startDate: '2024-06-01', endDate: '2024-06-03' };
    render(<CampaignInfoTab campaign={campaign} onSave={onSave} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });

    act(() => { vi.advanceTimersByTime(800); });

    expect(onSave).not.toHaveBeenCalled();
  });

  it('flushes a pending edit on unmount (e.g. switching tabs before the debounce fires)', () => {
    const onSave = vi.fn();
    const { unmount } = render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });

    unmount();

    expect(onSave).toHaveBeenCalledWith({ name: 'Nouveau nom' }, true);
  });

  it('manual save cancels the pending autosave and sends the full diff without silent flag', () => {
    const onSave = vi.fn();
    render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });

    fireEvent.click(screen.getByText('editor.info.save'));
    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave).toHaveBeenCalledWith({ name: 'Nouveau nom' });

    act(() => { vi.advanceTimersByTime(800); });
    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it('disables the manual save button while the date range is invalid', () => {
    const campaign = { ...baseCampaign, startDate: '2024-06-01', endDate: '2024-06-03' };
    render(<CampaignInfoTab campaign={campaign} onSave={vi.fn()} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });
    expect(screen.getByText('editor.info.save')).toBeDisabled();
  });
});
