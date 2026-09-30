import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { CampaignStoryTab } from '../CampaignStoryTab';
import { getCampaignStory, upsertCampaignStory, uploadStoryImage } from '@/lib/api/campaign';

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
  uploadStoryImage: vi.fn(),
}));

const mockAddToast = vi.fn();
vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: mockAddToast }),
}));

// Tiptap/ProseMirror needs a real contentEditable DOM this suite doesn't need to exercise --
// CampaignStoryTab's own logic (autosave, publish race, summary field, image upload wiring) is
// what's under test here. RichTextEditor renders real Tiptap and has its own smoke test.
vi.mock('@/components/ui/RichTextEditor', () => ({
  RichTextEditor: ({
    value,
    onChange,
    onImageUpload,
  }: {
    value: string;
    onChange: (html: string) => void;
    onImageUpload: (file: File) => Promise<string>;
  }) => (
    <div>
      <textarea data-testid="story-text" value={value} onChange={(e) => onChange(e.target.value)} />
      <button
        type="button"
        onClick={() => {
          // handleImageUpload deliberately re-throws after toasting (so RichTextEditor's own
          // try/catch can skip inserting the image) -- the real RichTextEditor awaits it inside a
          // try/catch; this stub must do the same or the rejection surfaces as unhandled here.
          // The real component does `editor.chain()...setImage({ src: url })` with the resolved
          // URL -- reproduced here as inserting it into the textarea, so tests can assert on it.
          onImageUpload(new File(['x'], 'x.png', { type: 'image/png' }))
            .then((url) => onChange(`<img src="${url}">`))
            .catch(() => {});
        }}
      >
        trigger-image-upload
      </button>
    </div>
  ),
}));

describe('CampaignStoryTab', () => {
  it('autosaves the draft text and summary together, without publishing', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue(null);
    vi.mocked(upsertCampaignStory).mockResolvedValue({
      storyText: '<p>Un récit.</p>', storySummary: 'Résumé.', publishedAt: null,
    });
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByTestId('story-text')).toBeInTheDocument());

    // Fake timers only enabled once the initial (real) async load has settled, so waitFor's own
    // internal polling timer isn't starved by them.
    vi.useFakeTimers();
    fireEvent.change(screen.getByLabelText('editor.story.summaryLabel'), { target: { value: 'Résumé.' } });
    fireEvent.change(screen.getByTestId('story-text'), { target: { value: '<p>Un récit.</p>' } });
    await act(async () => {
      vi.advanceTimersByTime(800);
    });
    vi.useRealTimers();

    expect(upsertCampaignStory).toHaveBeenCalledTimes(1);
    expect(upsertCampaignStory).toHaveBeenCalledWith('camp-1', {
      storyText: '<p>Un récit.</p>',
      storySummary: 'Résumé.',
      publish: false,
    });
    expect(screen.getByText('editor.story.draft')).toBeInTheDocument();
  });

  it('publishes explicitly, distinct from autosave, with no duplicate write', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue(null);
    vi.mocked(upsertCampaignStory).mockResolvedValue({
      storyText: '<p>Un récit fraîchement tapé.</p>',
      storySummary: 'Résumé.',
      publishedAt: '2026-09-29T10:00:00Z',
    });
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByTestId('story-text')).toBeInTheDocument());
    // Typing schedules a pending autosave; clicking Publier immediately after (well inside the
    // 800ms debounce window, no fake timers advanced) must cancel it rather than race it --
    // otherwise the first publish double-inserts and hits the campaign's unique constraint.
    fireEvent.change(screen.getByLabelText('editor.story.summaryLabel'), { target: { value: 'Résumé.' } });
    fireEvent.change(screen.getByTestId('story-text'), {
      target: { value: '<p>Un récit fraîchement tapé.</p>' },
    });
    fireEvent.click(screen.getByText('editor.story.publish'));

    await waitFor(() => expect(screen.getByText('editor.story.published')).toBeInTheDocument());
    expect(upsertCampaignStory).toHaveBeenCalledTimes(1);
    expect(upsertCampaignStory).toHaveBeenCalledWith('camp-1', {
      storyText: '<p>Un récit fraîchement tapé.</p>',
      storySummary: 'Résumé.',
      publish: true,
    });
  });

  it('publish stays disabled until both the story and the summary are filled', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue(null);
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByTestId('story-text')).toBeInTheDocument());
    expect(screen.getByText('editor.story.publish')).toBeDisabled();

    fireEvent.change(screen.getByLabelText('editor.story.summaryLabel'), { target: { value: 'Résumé.' } });
    expect(screen.getByText('editor.story.publish')).toBeDisabled();

    fireEvent.change(screen.getByTestId('story-text'), { target: { value: '<p>Un récit.</p>' } });
    expect(screen.getByText('editor.story.publish')).not.toBeDisabled();
  });

  it('shows the published state on load when the story is already live', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue({
      storyText: '<p>Déjà publié.</p>',
      storySummary: 'Résumé publié.',
      publishedAt: '2026-09-01T10:00:00Z',
    });
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByText('editor.story.published')).toBeInTheDocument());
    expect(screen.getByDisplayValue('<p>Déjà publié.</p>')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Résumé publié.')).toBeInTheDocument();
  });

  it('does not render an editable draft after a failed load', async () => {
    vi.mocked(getCampaignStory).mockRejectedValue(new Error('network error'));
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByText('editor.story.loadError')).toBeInTheDocument());
    expect(screen.queryByTestId('story-text')).not.toBeInTheDocument();
  });

  it('uploads an image and resolves its public URL for the editor to insert', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue(null);
    vi.mocked(uploadStoryImage).mockResolvedValue({ id: 'img-1', url: '/api/public/campaigns/camp-1/story-images/img-1' });
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByTestId('story-text')).toBeInTheDocument());
    fireEvent.click(screen.getByText('trigger-image-upload'));

    await waitFor(() => expect(uploadStoryImage).toHaveBeenCalledWith('camp-1', expect.any(File)));
  });

  it('resolves the uploaded image URL to an absolute one before the editor inserts it', async () => {
    // Regression: the server returns a *relative* URL (/api/public/campaigns/...) -- baking that
    // verbatim into storyText's HTML makes the <img> 404 wherever the HTML is later rendered
    // (it resolves against that page's own origin, not the API). Must match `campaignCoverUrl`'s
    // existing apiUrl() resolution for the same class of problem.
    vi.mocked(getCampaignStory).mockResolvedValue(null);
    vi.mocked(uploadStoryImage).mockResolvedValue({
      id: 'img-1',
      url: '/api/public/campaigns/camp-1/story-images/img-1',
    });
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByTestId('story-text')).toBeInTheDocument());
    fireEvent.click(screen.getByText('trigger-image-upload'));

    await waitFor(() =>
      expect(screen.getByTestId('story-text')).toHaveValue(
        '<img src="http://localhost:8080/api/public/campaigns/camp-1/story-images/img-1">',
      ),
    );
  });

  it('toasts an error and never resolves when the image upload fails', async () => {
    vi.mocked(getCampaignStory).mockResolvedValue(null);
    vi.mocked(uploadStoryImage).mockRejectedValue(new Error('upload failed'));
    render(<CampaignStoryTab campaignId="camp-1" />);

    await waitFor(() => expect(screen.getByTestId('story-text')).toBeInTheDocument());
    fireEvent.click(screen.getByText('trigger-image-upload'));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('error', 'campaignStoryImageUploadFailed'));
  });
});
