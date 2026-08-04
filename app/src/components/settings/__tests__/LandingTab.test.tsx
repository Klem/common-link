import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LandingTab } from '../LandingTab';
import type { AssociationProfileDto } from '@/types/association';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/campaign', () => ({
  getCampaigns: vi.fn(),
}));

// Mocked to prove the tab is read-only: none of these must ever be called.
vi.mock('@/lib/api/association', () => ({
  generateWidgetToken: vi.fn(),
  deleteWidgetToken: vi.fn(),
  updateAssociationProfile: vi.fn(),
  updateWidgetConfig: vi.fn(),
}));

import { getCampaigns } from '@/lib/api/campaign';
import {
  generateWidgetToken,
  deleteWidgetToken,
  updateAssociationProfile,
  updateWidgetConfig,
} from '@/lib/api/association';

const mockGetCampaigns = getCampaigns as ReturnType<typeof vi.fn>;

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
  addressLine1: null,
  legalObject: null,
  signerName: null,
  signerRole: null,
  verificationStatus: 'UNVERIFIED',
  verificationRejectionReason: null,
  widgetToken: null,
  widgetDestinationCampaignId: null,
  widgetAllowedOrigin: null,
};

const liveProfile: AssociationProfileDto = {
  ...baseProfile,
  widgetToken: 'clk_abc',
  widgetDestinationCampaignId: 'camp-live',
};

const onGoToWidget = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  mockGetCampaigns.mockResolvedValue([liveCampaign, draftCampaign]);
});

describe('LandingTab', () => {
  it('shows the token as inactive and no campaign when nothing is configured', async () => {
    render(<LandingTab profile={baseProfile} onGoToWidget={onGoToWidget} />);
    await waitFor(() => screen.getByText('snippet.noToken'));
    expect(screen.getByText('prereq.tokenInactive')).toBeInTheDocument();
    expect(screen.getByText('prereq.campaignNone')).toBeInTheDocument();
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
  });

  it('hides the snippets when the destination campaign is not LIVE', async () => {
    const profile = { ...baseProfile, widgetToken: 'clk_abc', widgetDestinationCampaignId: 'camp-draft' };
    render(<LandingTab profile={profile} onGoToWidget={onGoToWidget} />);
    await waitFor(() => screen.getByText('snippet.noLiveCampaign'));
    expect(screen.getByText('prereq.campaignNotLive')).toBeInTheDocument();
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
  });

  it('shows the three snippets when token and LIVE campaign are set', async () => {
    render(<LandingTab profile={liveProfile} onGoToWidget={onGoToWidget} />);
    await waitFor(() => screen.getByText('snippet.title'));
    expect(screen.getByText('prereq.tokenActive')).toBeInTheDocument();
    expect(screen.getByText(/Campagne Live/)).toBeInTheDocument();
    // Direct URL appears twice: standalone link, and inside the iframe fallback snippet.
    expect(screen.getAllByText(/\/fr\/lp\/clk_abc/).length).toBe(2);
    expect(screen.getByText(/landing\.js/)).toBeInTheDocument();
    expect(screen.getByText(/^<iframe/)).toBeInTheDocument();
    expect(screen.queryByText('snippet.noToken')).not.toBeInTheDocument();
  });

  it('delegates configuration to the Widget tab', async () => {
    render(<LandingTab profile={liveProfile} onGoToWidget={onGoToWidget} />);
    await waitFor(() => screen.getByText('prereq.goToWidget'));
    fireEvent.click(screen.getByText('prereq.goToWidget'));
    expect(onGoToWidget).toHaveBeenCalledTimes(1);
  });

  it('never writes: no association mutation is triggered', async () => {
    render(<LandingTab profile={liveProfile} onGoToWidget={onGoToWidget} />);
    await waitFor(() => screen.getByText('snippet.title'));
    fireEvent.click(screen.getByText('prereq.goToWidget'));
    expect(generateWidgetToken).not.toHaveBeenCalled();
    expect(deleteWidgetToken).not.toHaveBeenCalled();
    expect(updateAssociationProfile).not.toHaveBeenCalled();
    expect(updateWidgetConfig).not.toHaveBeenCalled();
  });
});
