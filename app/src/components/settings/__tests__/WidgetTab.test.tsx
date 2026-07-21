import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { WidgetTab } from '../WidgetTab';
import type { AssociationProfileDto } from '@/types/association';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/campaign', () => ({
  getCampaigns: vi.fn(),
}));

vi.mock('@/lib/api/association', () => ({
  generateWidgetToken: vi.fn(),
  deleteWidgetToken: vi.fn(),
  updateAssociationProfile: vi.fn(),
}));

vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

import { getCampaigns } from '@/lib/api/campaign';
import { generateWidgetToken, updateAssociationProfile } from '@/lib/api/association';

const mockGetCampaigns = getCampaigns as ReturnType<typeof vi.fn>;
const mockGenerateWidgetToken = generateWidgetToken as ReturnType<typeof vi.fn>;
const mockUpdateAssociationProfile = updateAssociationProfile as ReturnType<typeof vi.fn>;

const liveCampaign = {
  id: 'camp-live',
  name: 'Campagne Live',
  emoji: '🎗',
  description: null,
  goal: 1000,
  raised: 200,
  status: 'LIVE',
  startDate: null,
  endDate: null,
  milestoneCount: 0,
  createdAt: '2026-01-01T00:00:00Z',
};

const draftCampaign = {
  ...liveCampaign,
  id: 'camp-draft',
  name: 'Campagne Draft',
  status: 'DRAFT',
};

const baseProfile: AssociationProfileDto = {
  id: 'asso-1',
  name: 'Asso Test',
  identifier: 'W123',
  city: null,
  postalCode: null,
  contactName: null,
  description: null,
  siren: null,
  creationYear: null,
  contactEmail: null,
  phone: null,
  verificationStatus: 'UNVERIFIED',
  verificationRejectionReason: null,
  widgetToken: null,
  widgetDestinationCampaignId: null,
  widgetAllowedOrigin: null,
};

const onTokenChanged = vi.fn().mockResolvedValue(undefined);

beforeEach(() => {
  vi.clearAllMocks();
  mockGetCampaigns.mockResolvedValue([liveCampaign, draftCampaign]);
  onTokenChanged.mockResolvedValue(undefined);
});

describe('WidgetTab', () => {
  it('renders campaign dropdown with loaded campaigns', async () => {
    render(<WidgetTab profile={baseProfile} onTokenChanged={onTokenChanged} />);
    await waitFor(() => expect(screen.getByText(/Campagne Live/)).toBeInTheDocument());
    expect(screen.getByText(/Campagne Draft/)).toBeInTheDocument();
  });

  it('shows warning when non-LIVE campaign is selected', async () => {
    const profile = { ...baseProfile, widgetDestinationCampaignId: 'camp-draft' };
    render(<WidgetTab profile={profile} onTokenChanged={onTokenChanged} />);
    await waitFor(() => screen.getByText(/Campagne Draft/));
    expect(screen.getByText('destination.warningNotLive')).toBeInTheDocument();
  });

  it('calls updateAssociationProfile when campaign changes', async () => {
    mockUpdateAssociationProfile.mockResolvedValue({});
    render(<WidgetTab profile={baseProfile} onTokenChanged={onTokenChanged} />);
    await waitFor(() => screen.getByText(/Campagne Live/));

    const select = screen.getByRole('combobox');
    await act(async () => {
      fireEvent.change(select, { target: { value: 'camp-live' } });
    });

    expect(mockUpdateAssociationProfile).toHaveBeenCalledWith({
      widgetDestinationCampaignId: 'camp-live',
    });
  });

  it('shows generate button when no token, calls generateWidgetToken on click', async () => {
    mockGenerateWidgetToken.mockResolvedValue({ widgetToken: 'clk_abc123' });
    render(<WidgetTab profile={baseProfile} onTokenChanged={onTokenChanged} />);

    const generateBtn = await screen.findByText('token.generate');
    await act(async () => { fireEvent.click(generateBtn); });

    expect(mockGenerateWidgetToken).toHaveBeenCalledTimes(1);
    expect(onTokenChanged).toHaveBeenCalledTimes(1);
  });

  it('shows regenerate button when token exists', async () => {
    const profile = { ...baseProfile, widgetToken: 'clk_existing' };
    render(<WidgetTab profile={profile} onTokenChanged={onTokenChanged} />);
    expect(screen.getByText('token.regenerate')).toBeInTheDocument();
    expect(screen.getByText('token.disable')).toBeInTheDocument();
  });

  it('hides snippet code when no token', async () => {
    render(<WidgetTab profile={baseProfile} onTokenChanged={onTokenChanged} />);
    await waitFor(() => screen.getByText('snippet.noToken'));
    expect(screen.queryByText(/widget\.js/)).not.toBeInTheDocument();
  });

  it('hides snippet when token exists but campaign is not LIVE', async () => {
    const profile = {
      ...baseProfile,
      widgetToken: 'clk_abc',
      widgetDestinationCampaignId: 'camp-draft',
    };
    render(<WidgetTab profile={profile} onTokenChanged={onTokenChanged} />);
    await waitFor(() => screen.getByText('snippet.noLiveCampaign'));
    expect(screen.queryByText(/widget\.js/)).not.toBeInTheDocument();
  });

  it('shows snippet when token exists and campaign is LIVE', async () => {
    const profile = {
      ...baseProfile,
      widgetToken: 'clk_abc',
      widgetDestinationCampaignId: 'camp-live',
    };
    render(<WidgetTab profile={profile} onTokenChanged={onTokenChanged} />);
    await waitFor(() => screen.getAllByText('snippet.label'));
    expect(screen.getByText(/widget\.js/)).toBeInTheDocument();
    expect(screen.getByText(/embed\/donate\/clk_abc/)).toBeInTheDocument();
  });
});
