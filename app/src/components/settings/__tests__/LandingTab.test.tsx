import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LandingTab } from '../LandingTab';
import { LandingTheme } from '@/types/association';
import type { AssociationProfileDto } from '@/types/association';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/campaign', () => ({
  getCampaigns: vi.fn(),
}));

vi.mock('@/lib/api/association', () => ({
  // Landing configuration — the tab may call these.
  updateLandingConfig: vi.fn(),
  uploadLandingLogo: vi.fn(),
  deleteLandingLogo: vi.fn(),
  createLandingPreviewSession: vi.fn(),
  // Token and destination campaign belong to the Widget tab — these must never be called.
  generateWidgetToken: vi.fn(),
  deleteWidgetToken: vi.fn(),
  updateAssociationProfile: vi.fn(),
  updateWidgetConfig: vi.fn(),
}));

const addToast = vi.fn();
vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast }),
}));

import { getCampaigns } from '@/lib/api/campaign';
import {
  updateLandingConfig,
  uploadLandingLogo,
  deleteLandingLogo,
  createLandingPreviewSession,
  generateWidgetToken,
  deleteWidgetToken,
  updateAssociationProfile,
  updateWidgetConfig,
} from '@/lib/api/association';

const mockGetCampaigns = getCampaigns as ReturnType<typeof vi.fn>;
const mockUpdateLandingConfig = updateLandingConfig as ReturnType<typeof vi.fn>;
const mockUploadLandingLogo = uploadLandingLogo as ReturnType<typeof vi.fn>;
const mockDeleteLandingLogo = deleteLandingLogo as ReturnType<typeof vi.fn>;
const mockCreatePreviewSession = createLandingPreviewSession as ReturnType<typeof vi.fn>;

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
  landingTheme: LandingTheme.DEFAULT,
  landingLogo: null,
  landingShowProject: true,
  landingShowTransparency: true,
  landingShowTrust: true,
};

const liveProfile: AssociationProfileDto = {
  ...baseProfile,
  widgetToken: 'clk_abc',
  widgetDestinationCampaignId: 'camp-live',
};

const onGoToWidget = vi.fn();
const onConfigChanged = vi.fn().mockResolvedValue(undefined);

function renderTab(profile: AssociationProfileDto = liveProfile) {
  return render(
    <LandingTab
      profile={profile}
      onGoToWidget={onGoToWidget}
      onConfigChanged={onConfigChanged}
    />,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockGetCampaigns.mockResolvedValue([liveCampaign, draftCampaign]);
  onConfigChanged.mockResolvedValue(undefined);
  mockUpdateLandingConfig.mockResolvedValue(liveProfile);
  mockUploadLandingLogo.mockResolvedValue(liveProfile);
  mockDeleteLandingLogo.mockResolvedValue(liveProfile);
  mockCreatePreviewSession.mockResolvedValue({
    previewToken: 'prev_jwt',
    expiresAt: '2026-08-04T12:00:00Z',
  });
});

describe('LandingTab — prerequisites and snippets', () => {
  it('shows the token as inactive and no campaign when nothing is configured', async () => {
    renderTab(baseProfile);
    await waitFor(() => screen.getByText('snippet.noToken'));
    expect(screen.getByText('prereq.tokenInactive')).toBeInTheDocument();
    expect(screen.getByText('prereq.campaignNone')).toBeInTheDocument();
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
  });

  it('hides the snippets when the destination campaign is not LIVE', async () => {
    renderTab({ ...baseProfile, widgetToken: 'clk_abc', widgetDestinationCampaignId: 'camp-draft' });
    await waitFor(() => screen.getByText('snippet.noLiveCampaign'));
    expect(screen.getByText('prereq.campaignNotLive')).toBeInTheDocument();
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
  });

  it('shows the three snippets when token and LIVE campaign are set', async () => {
    renderTab();
    await waitFor(() => screen.getByText('snippet.title'));
    expect(screen.getByText('prereq.tokenActive')).toBeInTheDocument();
    expect(screen.getByText(/Campagne Live/)).toBeInTheDocument();
    // Direct URL appears twice: standalone link, and inside the iframe fallback snippet.
    expect(screen.getAllByText(/\/fr\/lp\/clk_abc/).length).toBe(2);
    expect(screen.getByText(/landing\.js/)).toBeInTheDocument();
    expect(screen.getByText(/^<iframe/)).toBeInTheDocument();
  });

  it('delegates token and campaign configuration to the Widget tab', async () => {
    renderTab();
    await waitFor(() => screen.getByText('prereq.goToWidget'));
    fireEvent.click(screen.getByText('prereq.goToWidget'));

    expect(onGoToWidget).toHaveBeenCalledTimes(1);
    expect(generateWidgetToken).not.toHaveBeenCalled();
    expect(deleteWidgetToken).not.toHaveBeenCalled();
    expect(updateAssociationProfile).not.toHaveBeenCalled();
    expect(updateWidgetConfig).not.toHaveBeenCalled();
  });
});

describe('LandingTab — appearance', () => {
  it('sends only the theme when a palette is picked', async () => {
    renderTab();
    await waitFor(() => screen.getByText('appearance.themes.NATURE'));

    fireEvent.click(screen.getByText('appearance.themes.NATURE'));

    await waitFor(() => expect(mockUpdateLandingConfig).toHaveBeenCalledTimes(1));
    // One field per interaction: the backend must not receive section flags it did not ask for.
    expect(mockUpdateLandingConfig).toHaveBeenCalledWith({ theme: 'NATURE' });
    expect(onConfigChanged).toHaveBeenCalledTimes(1);
  });

  it('marks the stored palette as selected', async () => {
    renderTab({ ...liveProfile, landingTheme: LandingTheme.SOBER });
    await waitFor(() => screen.getByText('appearance.themes.SOBER'));

    const selected = screen.getByText('appearance.themes.SOBER').closest('button');
    expect(selected).toHaveAttribute('aria-pressed', 'true');
    const other = screen.getByText('appearance.themes.WARM').closest('button');
    expect(other).toHaveAttribute('aria-pressed', 'false');
  });

  it('rejects a logo with an unsupported type before any upload', async () => {
    renderTab();
    await waitFor(() => screen.getByText('appearance.logoNone'));

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const svg = new File(['<svg/>'], 'logo.svg', { type: 'image/svg+xml' });
    fireEvent.change(input, { target: { files: [svg] } });

    await waitFor(() => expect(addToast).toHaveBeenCalledWith('error', 'landingLogoType'));
    expect(mockUploadLandingLogo).not.toHaveBeenCalled();
  });

  it('uploads an accepted logo', async () => {
    renderTab();
    await waitFor(() => screen.getByText('appearance.logoNone'));

    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const png = new File(['x'], 'logo.png', { type: 'image/png' });
    fireEvent.change(input, { target: { files: [png] } });

    await waitFor(() => expect(mockUploadLandingLogo).toHaveBeenCalledTimes(1));
    expect(onConfigChanged).toHaveBeenCalled();
  });

  it('offers removal when a logo is set', async () => {
    renderTab({ ...liveProfile, landingLogo: '/api/public/associations/asso-1/logo' });
    await waitFor(() => screen.getByText('appearance.logoRemove'));

    fireEvent.click(screen.getByText('appearance.logoRemove'));

    await waitFor(() => expect(mockDeleteLandingLogo).toHaveBeenCalledTimes(1));
  });
});

describe('LandingTab — sections', () => {
  it('sends only the toggled section flag', async () => {
    renderTab();
    await waitFor(() => screen.getByText('sections.showTransparency'));

    const toggles = screen.getAllByRole('checkbox');
    fireEvent.click(toggles[1]);

    await waitFor(() => expect(mockUpdateLandingConfig).toHaveBeenCalledTimes(1));
    expect(mockUpdateLandingConfig).toHaveBeenCalledWith({ showTransparency: false });
  });

  it('reflects the stored flags', async () => {
    renderTab({ ...liveProfile, landingShowTrust: false });
    await waitFor(() => screen.getByText('sections.showTrust'));

    const toggles = screen.getAllByRole('checkbox') as HTMLInputElement[];
    expect(toggles[0].checked).toBe(true);
    expect(toggles[2].checked).toBe(false);
  });
});

describe('LandingTab — preview modal', () => {
  it('requests a fresh preview token and builds the iframe URL', async () => {
    renderTab();
    await waitFor(() => screen.getByText('preview.open'));

    fireEvent.click(screen.getByText('preview.open'));

    await waitFor(() => expect(mockCreatePreviewSession).toHaveBeenCalledTimes(1));
    const frame = await waitFor(() => document.querySelector('iframe') as HTMLIFrameElement);
    expect(frame.getAttribute('src')).toContain('/fr/lp/clk_abc?preview=prev_jwt');
  });

  it('asks for a new token on every reload — a cached one would expire', async () => {
    renderTab();
    await waitFor(() => screen.getByText('preview.open'));
    fireEvent.click(screen.getByText('preview.open'));
    await waitFor(() => expect(mockCreatePreviewSession).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByText('reload'));

    await waitFor(() => expect(mockCreatePreviewSession).toHaveBeenCalledTimes(2));
  });

  it('shows an explicit error when the token cannot be issued', async () => {
    mockCreatePreviewSession.mockRejectedValue(new Error('boom'));
    renderTab();
    await waitFor(() => screen.getByText('preview.open'));

    fireEvent.click(screen.getByText('preview.open'));

    await waitFor(() => expect(screen.getByText('failed')).toBeInTheDocument());
    expect(document.querySelector('iframe')).toBeNull();
  });

  it('offers no preview button without a token', async () => {
    renderTab(baseProfile);
    await waitFor(() => screen.getByText('snippet.noToken'));

    expect(screen.queryByText('preview.open')).not.toBeInTheDocument();
  });
});
