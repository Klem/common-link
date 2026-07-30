import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { CampaignInfoTab } from '../CampaignInfoTab';
import { uploadCampaignCover, deleteCampaignCover } from '@/lib/api/campaign';
import type { CampaignDto } from '@/types/campaign';

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/campaign', () => ({
  uploadCampaignCover: vi.fn(),
  deleteCampaignCover: vi.fn(),
  campaignCoverUrl: (path: string, version?: string) =>
    `http://api.test${path}${version ? `?v=${encodeURIComponent(version)}` : ''}`,
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
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not autosave immediately when a field is edited', () => {
    const onSave = vi.fn();
    render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} onCoverChange={vi.fn()} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });
    expect(onSave).not.toHaveBeenCalled();
  });

  it('autosaves silently 800ms after a field edit', () => {
    const onSave = vi.fn();
    render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} onCoverChange={vi.fn()} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });

    act(() => { vi.advanceTimersByTime(800); });

    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave).toHaveBeenCalledWith({ name: 'Nouveau nom' }, true);
  });

  it('merges edits to several fields made within the debounce window into a single autosave', () => {
    const onSave = vi.fn();
    render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} onCoverChange={vi.fn()} isSaving={false} />);
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
    render(<CampaignInfoTab campaign={campaign} onSave={onSave} onCoverChange={vi.fn()} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });

    act(() => { vi.advanceTimersByTime(800); });

    expect(onSave).not.toHaveBeenCalled();
  });

  it('flushes a pending edit on unmount (e.g. switching tabs before the debounce fires)', () => {
    const onSave = vi.fn();
    const { unmount } = render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} onCoverChange={vi.fn()} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });

    unmount();

    expect(onSave).toHaveBeenCalledWith({ name: 'Nouveau nom' }, true);
  });

  it('manual save cancels the pending autosave and sends the full diff without silent flag', () => {
    const onSave = vi.fn();
    render(<CampaignInfoTab campaign={baseCampaign} onSave={onSave} onCoverChange={vi.fn()} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });

    fireEvent.click(screen.getByText('editor.info.save'));
    expect(onSave).toHaveBeenCalledTimes(1);
    expect(onSave).toHaveBeenCalledWith({ name: 'Nouveau nom' });

    act(() => { vi.advanceTimersByTime(800); });
    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it('disables the manual save button while the date range is invalid', () => {
    const campaign = { ...baseCampaign, startDate: '2024-06-01', endDate: '2024-06-03' };
    render(<CampaignInfoTab campaign={campaign} onSave={vi.fn()} onCoverChange={vi.fn()} isSaving={false} />);
    fireEvent.change(screen.getByDisplayValue('Test Campaign'), { target: { value: 'Nouveau nom' } });
    expect(screen.getByText('editor.info.save')).toBeDisabled();
  });

  it('exposes the field ids the hero completion pills scroll to', () => {
    const { container } = render(
      <CampaignInfoTab campaign={baseCampaign} onSave={vi.fn()} onCoverChange={vi.fn()} isSaving={false} />,
    );
    for (const id of ['info-goal', 'info-start', 'info-desc', 'info-reason', 'info-impact-goals']) {
      expect(container.querySelector(`#${id}`)).not.toBeNull();
    }
  });

  // ── Image de couverture ─────────────────────────────────────────────────────

  describe('cover image', () => {
    const pngFile = () => new File(['x'], 'cover.png', { type: 'image/png' });

    it('opens the file dialog when the upload zone is clicked', () => {
      const { container } = render(
        <CampaignInfoTab campaign={baseCampaign} onSave={vi.fn()} onCoverChange={vi.fn()} isSaving={false} />,
      );
      const input = container.querySelector('input[type="file"]') as HTMLInputElement;
      const click = vi.spyOn(input, 'click');

      fireEvent.click(container.querySelector('.upload') as HTMLElement);

      expect(click).toHaveBeenCalled();
    });

    it('uploads a dropped image and hands the returned DTO to onCoverChange', async () => {
      const updated = { ...baseCampaign, coverImage: '/api/public/campaigns/camp-1/cover' };
      vi.mocked(uploadCampaignCover).mockResolvedValue(updated);
      const onCoverChange = vi.fn();
      const { container } = render(
        <CampaignInfoTab campaign={baseCampaign} onSave={vi.fn()} onCoverChange={onCoverChange} isSaving={false} />,
      );
      const file = pngFile();

      fireEvent.drop(container.querySelector('.upload') as HTMLElement, { dataTransfer: { files: [file] } });
      await act(async () => { await Promise.resolve(); });

      expect(uploadCampaignCover).toHaveBeenCalledWith('camp-1', file);
      expect(onCoverChange).toHaveBeenCalledWith(updated);
    });

    it('rejects an unsupported type without any network call', async () => {
      const { container } = render(
        <CampaignInfoTab campaign={baseCampaign} onSave={vi.fn()} onCoverChange={vi.fn()} isSaving={false} />,
      );
      const pdf = new File(['x'], 'doc.pdf', { type: 'application/pdf' });

      fireEvent.drop(container.querySelector('.upload') as HTMLElement, { dataTransfer: { files: [pdf] } });
      await act(async () => { await Promise.resolve(); });

      expect(uploadCampaignCover).not.toHaveBeenCalled();
      expect(screen.getByText(/coverImage.errorType/)).toBeInTheDocument();
    });

    it('rejects a file above 5 MB without any network call', async () => {
      const { container } = render(
        <CampaignInfoTab campaign={baseCampaign} onSave={vi.fn()} onCoverChange={vi.fn()} isSaving={false} />,
      );
      const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' });

      fireEvent.drop(container.querySelector('.upload') as HTMLElement, { dataTransfer: { files: [big] } });
      await act(async () => { await Promise.resolve(); });

      expect(uploadCampaignCover).not.toHaveBeenCalled();
      expect(screen.getByText(/coverImage.errorSize/)).toBeInTheDocument();
    });

    it('shows the stored image and removes it on demand', async () => {
      const campaign = { ...baseCampaign, coverImage: '/api/public/campaigns/camp-1/cover' };
      vi.mocked(deleteCampaignCover).mockResolvedValue(baseCampaign);
      const onCoverChange = vi.fn();
      render(
        <CampaignInfoTab campaign={campaign} onSave={vi.fn()} onCoverChange={onCoverChange} isSaving={false} />,
      );

      // `updatedAt` en cache-buster : le chemin de service ne change pas d'une image à l'autre.
      expect(screen.getByRole('img')).toHaveAttribute(
        'src',
        'http://api.test/api/public/campaigns/camp-1/cover?v=2024-01-01T00%3A00%3A00Z',
      );

      fireEvent.click(screen.getByText('editor.info.coverImage.remove'));
      await act(async () => { await Promise.resolve(); });

      expect(deleteCampaignCover).toHaveBeenCalledWith('camp-1');
      expect(onCoverChange).toHaveBeenCalledWith(baseCampaign);
    });
  });
});
