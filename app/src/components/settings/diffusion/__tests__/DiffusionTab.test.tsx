import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { DiffusionTab } from '../DiffusionTab';
import { LandingTheme } from '@/types/association';
import type { AssociationProfileDto } from '@/types/association';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('@/lib/api/campaign', () => ({
  getCampaigns: vi.fn(),
}));

vi.mock('@/lib/api/association', () => ({
  updateLandingConfig: vi.fn(),
  uploadLandingLogo: vi.fn(),
  deleteLandingLogo: vi.fn(),
  createLandingPreviewSession: vi.fn(),
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
  updateAssociationProfile,
  updateWidgetConfig,
} from '@/lib/api/association';

const mockGetCampaigns = getCampaigns as ReturnType<typeof vi.fn>;
const mockUpdateLandingConfig = updateLandingConfig as ReturnType<typeof vi.fn>;
const mockUploadLandingLogo = uploadLandingLogo as ReturnType<typeof vi.fn>;
const mockDeleteLandingLogo = deleteLandingLogo as ReturnType<typeof vi.fn>;
const mockCreatePreviewSession = createLandingPreviewSession as ReturnType<typeof vi.fn>;
const mockGenerateWidgetToken = generateWidgetToken as ReturnType<typeof vi.fn>;
const mockUpdateAssociationProfile = updateAssociationProfile as ReturnType<typeof vi.fn>;
const mockUpdateWidgetConfig = updateWidgetConfig as ReturnType<typeof vi.fn>;

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

const draftCampaign = { ...liveCampaign, id: 'camp-draft', name: 'Campagne Draft', status: 'DRAFT' };

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
  gtmContainerId: null,
};

const liveProfile: AssociationProfileDto = {
  ...baseProfile,
  widgetToken: 'clk_abc',
  widgetDestinationCampaignId: 'camp-live',
};

const onProfileChanged = vi.fn().mockResolvedValue(undefined);

const renderTab = (profile: AssociationProfileDto = liveProfile) =>
  render(<DiffusionTab profile={profile} onProfileChanged={onProfileChanged} />);

beforeEach(() => {
  vi.clearAllMocks();
  mockGetCampaigns.mockResolvedValue([liveCampaign, draftCampaign]);
  onProfileChanged.mockResolvedValue(undefined);
  mockUpdateLandingConfig.mockResolvedValue(liveProfile);
  mockUploadLandingLogo.mockResolvedValue(liveProfile);
  mockDeleteLandingLogo.mockResolvedValue(liveProfile);
  mockCreatePreviewSession.mockResolvedValue({ previewToken: 'prev_jwt', expiresAt: '2026-08-04T12:00:00Z' });
});

describe('DiffusionTab — campaign and token', () => {
  it('lists the campaigns, loaded once for the whole tab', async () => {
    renderTab(baseProfile);
    await waitFor(() => expect(screen.getByText(/Campagne Live/)).toBeInTheDocument());
    expect(screen.getByText(/Campagne Draft/)).toBeInTheDocument();
    expect(mockGetCampaigns).toHaveBeenCalledTimes(1);
  });

  it('warns when the selected campaign is not LIVE', async () => {
    renderTab({ ...baseProfile, widgetDestinationCampaignId: 'camp-draft' });
    await waitFor(() => screen.getByText(/Campagne Draft/));
    expect(screen.getByText('campaign.warningNotLive')).toBeInTheDocument();
  });

  it('saves the destination campaign and refreshes the profile', async () => {
    mockUpdateAssociationProfile.mockResolvedValue({});
    renderTab(baseProfile);
    await waitFor(() => screen.getByText(/Campagne Live/));

    await act(async () => {
      fireEvent.change(screen.getByRole('combobox'), { target: { value: 'camp-live' } });
    });

    expect(mockUpdateAssociationProfile).toHaveBeenCalledWith({ widgetDestinationCampaignId: 'camp-live' });
    expect(onProfileChanged).toHaveBeenCalledTimes(1);
  });

  it('generates a token when there is none', async () => {
    mockGenerateWidgetToken.mockResolvedValue({ widgetToken: 'clk_abc123' });
    renderTab(baseProfile);

    await act(async () => { fireEvent.click(screen.getByText('token.generate')); });

    expect(mockGenerateWidgetToken).toHaveBeenCalledTimes(1);
    expect(onProfileChanged).toHaveBeenCalledTimes(1);
  });

  it('offers regenerate and disable when a token exists', () => {
    renderTab({ ...baseProfile, widgetToken: 'clk_existing' });
    expect(screen.getByText('token.regenerate')).toBeInTheDocument();
    expect(screen.getByText('token.disable')).toBeInTheDocument();
  });
});

describe('DiffusionTab — campaign page URL', () => {
  it('saves the full campaign page URL, path included, then refreshes the profile', async () => {
    mockUpdateWidgetConfig.mockResolvedValue(undefined);
    renderTab(baseProfile);

    fireEvent.change(screen.getByPlaceholderText('pageUrl.placeholder'), {
      target: { value: ' https://asso.fr/campagnes/ete ' },
    });
    fireEvent.click(screen.getByText('pageUrl.save'));

    await waitFor(() =>
      expect(mockUpdateWidgetConfig).toHaveBeenCalledWith({ widgetAllowedOrigin: 'https://asso.fr/campagnes/ete' }),
    );
    await waitFor(() => expect(onProfileChanged).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('pageUrl.error')).not.toBeInTheDocument();
  });

  it('refuses a value that is not an http(s) URL, without calling the API', async () => {
    renderTab(baseProfile);

    fireEvent.change(screen.getByPlaceholderText('pageUrl.placeholder'), { target: { value: 'asso.fr/campagne' } });
    fireEvent.click(screen.getByText('pageUrl.save'));

    expect(await screen.findByText('pageUrl.error')).toBeInTheDocument();
    expect(mockUpdateWidgetConfig).not.toHaveBeenCalled();
  });
});

describe('DiffusionTab — embed methods', () => {
  it('shows no code without a token', async () => {
    renderTab(baseProfile);
    await waitFor(() => screen.getByText('methods.noToken'));
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
    expect(screen.queryByText(/widget\.js/)).not.toBeInTheDocument();
  });

  it('shows no code when the destination campaign is not LIVE', async () => {
    renderTab({ ...baseProfile, widgetToken: 'clk_abc', widgetDestinationCampaignId: 'camp-draft' });
    await waitFor(() => screen.getByText('methods.noLiveCampaign'));
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
  });

  it('shows every method once the token and a LIVE campaign are set', async () => {
    renderTab();
    await waitFor(() => screen.getByText(/landing\.js/));
    // Direct URL appears twice: the direct link card, and inside the iframe code.
    expect(screen.getAllByText(/\/fr\/lp\/clk_abc/).length).toBe(2);
    expect(screen.getByText(/^<iframe/)).toBeInTheDocument();
    expect(screen.getByText(/widget\.js/)).toBeInTheDocument();
  });

  it('no longer offers the bare /embed/donate iframe URL, which could not relay any event', async () => {
    renderTab();
    await waitFor(() => screen.getByText(/landing\.js/));
    expect(screen.queryByText(/embed\/donate/)).not.toBeInTheDocument();
  });

  it('flags a missing campaign page URL in every card published on the association page', async () => {
    renderTab();
    await waitFor(() => screen.getByText(/landing\.js/));
    // Script, iframe and widget cards (no GTM here, so no HTML export card).
    expect(screen.getAllByText('methods.pageUrlMissing')).toHaveLength(3);
  });

  it('shows the configured campaign page URL instead', async () => {
    renderTab({ ...liveProfile, widgetAllowedOrigin: 'https://asso.fr/campagne' });
    await waitFor(() => screen.getByText(/landing\.js/));
    expect(screen.getAllByText('methods.pageUrlSet')).toHaveLength(3);
    expect(screen.queryByText('methods.pageUrlMissing')).not.toBeInTheDocument();
  });
});

describe('DiffusionTab — appearance', () => {
  it('sends only the theme when a palette is picked', async () => {
    renderTab();
    fireEvent.click(screen.getByText('appearance.themes.NATURE'));

    await waitFor(() => expect(mockUpdateLandingConfig).toHaveBeenCalledTimes(1));
    expect(mockUpdateLandingConfig).toHaveBeenCalledWith({ theme: 'NATURE' });
    expect(onProfileChanged).toHaveBeenCalledTimes(1);
  });

  it('marks the stored palette as selected', () => {
    renderTab({ ...liveProfile, landingTheme: LandingTheme.SOBER });
    expect(screen.getByText('appearance.themes.SOBER').closest('button')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('appearance.themes.WARM').closest('button')).toHaveAttribute('aria-pressed', 'false');
  });

  it('rejects a logo with an unsupported type before any upload', async () => {
    renderTab();
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [new File(['<svg/>'], 'logo.svg', { type: 'image/svg+xml' })] } });

    await waitFor(() => expect(addToast).toHaveBeenCalledWith('error', 'landingLogoType'));
    expect(mockUploadLandingLogo).not.toHaveBeenCalled();
  });

  it('uploads an accepted logo', async () => {
    renderTab();
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [new File(['x'], 'logo.png', { type: 'image/png' })] } });

    await waitFor(() => expect(mockUploadLandingLogo).toHaveBeenCalledTimes(1));
    expect(onProfileChanged).toHaveBeenCalled();
  });

  it('offers removal when a logo is set', async () => {
    renderTab({ ...liveProfile, landingLogo: '/api/public/associations/asso-1/logo' });
    fireEvent.click(screen.getByText('appearance.logoRemove'));
    await waitFor(() => expect(mockDeleteLandingLogo).toHaveBeenCalledTimes(1));
  });
});

describe('DiffusionTab — sections', () => {
  it('no longer offers any section toggle — every landing section is always shown', () => {
    renderTab();
    expect(screen.getByText('appearance.title')).toBeInTheDocument();
    expect(screen.queryAllByRole('checkbox')).toHaveLength(0);
  });
});

describe('DiffusionTab — preview modal', () => {
  it('requests a fresh preview token and builds the iframe URL', async () => {
    renderTab();
    fireEvent.click(screen.getByText('preview.open'));

    await waitFor(() => expect(mockCreatePreviewSession).toHaveBeenCalledTimes(1));
    const frame = await waitFor(() => document.querySelector('iframe') as HTMLIFrameElement);
    expect(frame.getAttribute('src')).toContain('/fr/lp/clk_abc?preview=prev_jwt');
  });

  it('asks for a new token on every reload — a cached one would expire', async () => {
    renderTab();
    fireEvent.click(screen.getByText('preview.open'));
    await waitFor(() => expect(mockCreatePreviewSession).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByText('reload'));

    await waitFor(() => expect(mockCreatePreviewSession).toHaveBeenCalledTimes(2));
  });

  it('shows an explicit error when the token cannot be issued', async () => {
    mockCreatePreviewSession.mockRejectedValue(new Error('boom'));
    renderTab();
    fireEvent.click(screen.getByText('preview.open'));

    await waitFor(() => expect(screen.getByText('failed')).toBeInTheDocument());
    expect(document.querySelector('iframe')).toBeNull();
  });

  it('offers no preview button without a token', () => {
    renderTab(baseProfile);
    expect(screen.queryByText('preview.open')).not.toBeInTheDocument();
  });
});

describe('DiffusionTab — GTM', () => {
  const mockFetch = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch);
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('saves a valid GTM container ID', async () => {
    renderTab(baseProfile);
    fireEvent.change(screen.getByPlaceholderText('tracking.placeholder'), { target: { value: 'GTM-ABC1234' } });
    fireEvent.click(screen.getByText('tracking.save'));

    await waitFor(() => expect(mockUpdateLandingConfig).toHaveBeenCalledWith({ gtmContainerId: 'GTM-ABC1234' }));
  });

  it('rejects a malformed GTM container ID without calling the backend', () => {
    renderTab(baseProfile);
    // Lowercase — mirrors the backend pattern (UpdateLandingConfigRequest.gtmContainerId).
    fireEvent.change(screen.getByPlaceholderText('tracking.placeholder'), { target: { value: 'GTM-abcd' } });
    fireEvent.click(screen.getByText('tracking.save'));

    expect(screen.getByText('tracking.error')).toBeInTheDocument();
    expect(mockUpdateLandingConfig).not.toHaveBeenCalled();
  });

  it('sends an empty value to clear a configured GTM container ID', async () => {
    renderTab({ ...baseProfile, gtmContainerId: 'GTM-ABC1234' });
    fireEvent.change(screen.getByDisplayValue('GTM-ABC1234'), { target: { value: '' } });
    fireEvent.click(screen.getByText('tracking.save'));

    await waitFor(() => expect(mockUpdateLandingConfig).toHaveBeenCalledWith({ gtmContainerId: '' }));
  });

  it('offers the head/body tags once, only with a configured container', () => {
    renderTab(baseProfile);
    expect(screen.queryByText('tracking.tags.headLabel')).not.toBeInTheDocument();
  });

  it('offers the head/body tags once a container is configured', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    renderTab({ ...liveProfile, gtmContainerId: 'GTM-ABC1234' });
    expect(screen.getAllByText('tracking.tags.headLabel')).toHaveLength(1);
    expect(screen.getAllByText('tracking.tags.bodyLabel')).toHaveLength(1);
  });

  it('hides the export without a configured GTM container ID', async () => {
    renderTab(liveProfile);
    await waitFor(() => screen.getByText(/landing\.js/));
    expect(screen.queryByText('methods.export.title')).not.toBeInTheDocument();
  });

  it('hides the export when GTM is configured but no widget token exists', async () => {
    renderTab({ ...baseProfile, gtmContainerId: 'GTM-ABC1234' });
    await waitFor(() => screen.getByText('methods.noToken'));
    expect(screen.queryByText('methods.export.title')).not.toBeInTheDocument();
  });

  it('hides the export when the destination campaign is not LIVE, even with GTM configured', async () => {
    renderTab({ ...baseProfile, widgetToken: 'clk_abc', widgetDestinationCampaignId: 'camp-draft', gtmContainerId: 'GTM-ABC1234' });
    await waitFor(() => screen.getByText('methods.noLiveCampaign'));
    expect(screen.queryByText('methods.export.title')).not.toBeInTheDocument();
  });

  it('fetches the export from /api/gtm-export once GTM, the token and a LIVE campaign are set', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ html: '<!DOCTYPE html><html><body>real landing content</body></html>' }),
    });

    renderTab({ ...liveProfile, gtmContainerId: 'GTM-ABC1234' });

    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('/api/gtm-export/clk_abc?gtmId=GTM-ABC1234'));
    await waitFor(() => screen.getByText('methods.export.download'));
    const textarea = screen.getByTestId('gtm-export-textarea') as HTMLTextAreaElement;
    expect(textarea.value).toContain('real landing content');
  });

  it('shows an explicit error and offers a retry when the export fails', async () => {
    mockFetch.mockRejectedValue(new Error('boom'));

    renderTab({ ...liveProfile, gtmContainerId: 'GTM-ABC1234' });

    await waitFor(() => screen.getByText('methods.export.failed'));
    expect(screen.queryByTestId('gtm-export-textarea')).not.toBeInTheDocument();

    mockFetch.mockResolvedValue({ ok: true, json: async () => ({ html: '<!DOCTYPE html><html><body>ok</body></html>' }) });
    fireEvent.click(screen.getByText('methods.export.reload'));

    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(2));
    await waitFor(() => screen.getByText('methods.export.download'));
  });
});
