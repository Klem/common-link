import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { CampaignStoryModal } from '../CampaignStoryModal';
import { getCampaignReport } from '@/lib/api/donor';
import type { DonorCampaignReportDto } from '@/types/donor';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/donor', () => ({
  getCampaignReport: vi.fn(),
}));

const report = {
  campaignId: 'camp-1',
  story: { storyText: '<p>Un récit <strong>complet</strong>.</p>', storySummary: 'Résumé.', publishedAt: '2026-09-01T10:00:00Z' },
} as unknown as DonorCampaignReportDto;

describe('CampaignStoryModal', () => {
  it('renders nothing when campaignId is null', () => {
    const { container } = render(<CampaignStoryModal campaignId={null} onClose={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows a loading state, then renders the story HTML once fetched', async () => {
    vi.mocked(getCampaignReport).mockResolvedValue(report);
    render(<CampaignStoryModal campaignId="camp-1" onClose={vi.fn()} />);

    expect(screen.getByText('loading')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('complet', { exact: false })).toBeInTheDocument());
    expect(getCampaignReport).toHaveBeenCalledWith('camp-1');
  });

  it('shows an error state when the fetch fails', async () => {
    vi.mocked(getCampaignReport).mockRejectedValue(new Error('network error'));
    render(<CampaignStoryModal campaignId="camp-1" onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('error')).toBeInTheDocument());
  });

  it('calls onClose when the close button is clicked', async () => {
    vi.mocked(getCampaignReport).mockResolvedValue(report);
    const onClose = vi.fn();
    render(<CampaignStoryModal campaignId="camp-1" onClose={onClose} />);

    await waitFor(() => expect(screen.getByText('complet', { exact: false })).toBeInTheDocument());
    screen.getByLabelText('close').click();
    expect(onClose).toHaveBeenCalled();
  });
});
