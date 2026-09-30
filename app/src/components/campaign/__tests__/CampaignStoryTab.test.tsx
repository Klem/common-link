import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { CampaignStoryTab } from '../CampaignStoryTab';
import { getCampaignStory, upsertCampaignStory } from '@/lib/api/campaign';

// This suite asserts exact call counts (no duplicate publish write) -- the mock call history must
// not leak across tests within the file, since vitest.config.ts sets no global `clearMocks`.
beforeEach(() => {
  vi.clearAllMocks();
});

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/campaign', () => ({
  getCampaignStory: vi.fn(),
  upsertCampaignStory: vi.fn(),
}));

vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

describe('CampaignStoryTab', () => {
  it('autosaves the draft text without publishing', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue(null);
    vi.mocked(upsertCampaignStory).mockResolvedValue({ storyText: 'Un récit.', publishedAt: null });
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByLabelText('editor.story.label')).toBeInTheDocument());

    // Fake timers only enabled once the initial (real) async load has settled, so waitFor's own
    // internal polling timer isn't starved by them.
    vi.useFakeTimers();
    fireEvent.change(screen.getByLabelText('editor.story.label'), { target: { value: 'Un récit.' } });
    await act(async () => {
      vi.advanceTimersByTime(800);
    });
    vi.useRealTimers();

    expect(upsertCampaignStory).toHaveBeenCalledWith('camp-1', { storyText: 'Un récit.', publish: false });
    expect(screen.getByText('editor.story.draft')).toBeInTheDocument();
  });

  it('publishes explicitly, distinct from autosave, with no duplicate write', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue(null);
    vi.mocked(upsertCampaignStory).mockResolvedValue({
      storyText: 'Un récit fraîchement tapé.',
      publishedAt: '2026-09-29T10:00:00Z',
    });
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByLabelText('editor.story.label')).toBeInTheDocument());
    // Typing schedules a pending autosave; clicking Publier immediately after (well inside the
    // 800ms debounce window, no fake timers advanced) must cancel it rather than race it —
    // otherwise the first publish double-inserts and hits the campaign's unique constraint.
    fireEvent.change(screen.getByLabelText('editor.story.label'), {
      target: { value: 'Un récit fraîchement tapé.' },
    });
    fireEvent.click(screen.getByText('editor.story.publish'));

    await waitFor(() => expect(screen.getByText('editor.story.published')).toBeInTheDocument());
    expect(upsertCampaignStory).toHaveBeenCalledTimes(1);
    expect(upsertCampaignStory).toHaveBeenCalledWith('camp-1', {
      storyText: 'Un récit fraîchement tapé.',
      publish: true,
    });
  });

  it('shows the published state on load when the story is already live', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue({
      storyText: 'Déjà publié.',
      publishedAt: '2026-09-01T10:00:00Z',
    });
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByText('editor.story.published')).toBeInTheDocument());
    expect(screen.getByDisplayValue('Déjà publié.')).toBeInTheDocument();
  });

  it('does not render an editable draft after a failed load', async () => {
    vi.mocked(getCampaignStory).mockRejectedValue(new Error('network error'));
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByText('editor.story.loadError')).toBeInTheDocument());
    expect(screen.queryByLabelText('editor.story.label')).not.toBeInTheDocument();
  });
});
