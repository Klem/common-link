import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { DiffusionTab } from '../DiffusionTab';
import { LandingTheme } from '@/types/association';
import { EmbedType } from '@/lib/gtm';
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
  it('offers only the LIVE campaigns, loaded once for the whole tab', async () => {
    renderTab(baseProfile);
    await waitFor(() => expect(screen.getByText(/Campagne Live/)).toBeInTheDocument());
    expect(screen.queryByText(/Campagne Draft/)).not.toBeInTheDocument();
    expect(mockGetCampaigns).toHaveBeenCalledTimes(1);
  });

  it('warns when the stored destination campaign is no longer LIVE', async () => {
    renderTab({ ...baseProfile, widgetDestinationCampaignId: 'camp-draft' });
    await waitFor(() => screen.getByText('campaign.warningNotLive'));
    expect(screen.queryByText(/Campagne Draft/)).not.toBeInTheDocument();
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

const withMethod = (embedType: EmbedType, extra: Partial<AssociationProfileDto> = {}): AssociationProfileDto => ({
  ...liveProfile,
  embedType,
  ...extra,
});

/** Renders the tab and waits for the campaigns: the methods only show once the destination is known LIVE. */
const renderLive = async (profile: AssociationProfileDto = liveProfile) => {
  const view = renderTab(profile);
  await screen.findAllByRole('radio');
  return view;
};

describe('DiffusionTab — campaign page URL', () => {
  it('saves the full campaign page URL, path included, then refreshes the profile', async () => {
    mockUpdateWidgetConfig.mockResolvedValue(undefined);
    await renderLive(withMethod(EmbedType.SCRIPT));

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
    await renderLive(withMethod(EmbedType.SCRIPT));

    fireEvent.change(screen.getByPlaceholderText('pageUrl.placeholder'), { target: { value: 'asso.fr/campagne' } });
    fireEvent.click(screen.getByText('pageUrl.save'));

    expect(await screen.findByText('pageUrl.error')).toBeInTheDocument();
    expect(mockUpdateWidgetConfig).not.toHaveBeenCalled();
  });

  it('is not asked for the direct link, whose donors come back to CommonLink', async () => {
    await renderLive(withMethod(EmbedType.DIRECT));
    expect(screen.queryByPlaceholderText('pageUrl.placeholder')).not.toBeInTheDocument();
    expect(screen.getByText('methods.status.noReturnUrl')).toBeInTheDocument();
  });

  it('flags a missing campaign page URL, then a set one', async () => {
    const { unmount } = await renderLive(withMethod(EmbedType.WIDGET));
    expect(screen.getByText('methods.status.returnUrlMissing')).toBeInTheDocument();
    unmount();

    await renderLive(withMethod(EmbedType.WIDGET, { widgetAllowedOrigin: 'https://asso.fr/campagne' }));
    expect(screen.getByText('methods.status.returnUrlSet')).toBeInTheDocument();
  });
});

describe('DiffusionTab — diffusion method', () => {
  it('shows no method without a token', async () => {
    renderTab(baseProfile);
    await waitFor(() => screen.getByText('methods.noToken'));
    expect(screen.queryAllByRole('radio')).toHaveLength(0);
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
  });

  it('shows no method when the destination campaign is not LIVE', async () => {
    renderTab({ ...baseProfile, widgetToken: 'clk_abc', widgetDestinationCampaignId: 'camp-draft' });
    await waitFor(() => screen.getByText('methods.noLiveCampaign'));
    expect(screen.queryAllByRole('radio')).toHaveLength(0);
  });

  it('offers the five methods as one radio group', async () => {
    await renderLive();
    expect(screen.getAllByRole('radio')).toHaveLength(5);
  });

  it('defaults to the direct link while nothing is stored and no campaign page URL is set', async () => {
    await renderLive();
    expect(screen.getByRole('radio', { name: /methods\.direct\.title/ })).toBeChecked();
    expect(screen.getByText(/\/fr\/lp\/clk_abc$/)).toBeInTheDocument();
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
  });

  it('defaults to the embedded page once a campaign page URL is set', async () => {
    await renderLive({ ...liveProfile, widgetAllowedOrigin: 'https://asso.fr/campagne' });
    expect(screen.getByRole('radio', { name: /methods\.script\.title/ })).toBeChecked();
    expect(screen.getByText(/landing\.js/)).toBeInTheDocument();
  });

  it('opens on the stored method and shows only its code', async () => {
    await renderLive(withMethod(EmbedType.WIDGET));
    expect(screen.getByRole('radio', { name: /methods\.widget\.title/ })).toBeChecked();
    expect(screen.getByText(/widget\.js/)).toBeInTheDocument();
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^<iframe/)).not.toBeInTheDocument();
  });

  it('stores the picked method and switches the settings to it', async () => {
    await renderLive(withMethod(EmbedType.SCRIPT));

    await act(async () => {
      fireEvent.click(screen.getByRole('radio', { name: /methods\.iframe\.title/ }));
    });

    expect(mockUpdateLandingConfig).toHaveBeenCalledWith({ embedType: 'iframe' });
    expect(onProfileChanged).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/^<iframe/)).toBeInTheDocument();
    expect(screen.queryByText(/landing\.js/)).not.toBeInTheDocument();
  });

  it('offers the iframe bridge script only once a GTM container is set', async () => {
    const { unmount } = await renderLive(withMethod(EmbedType.IFRAME));
    expect(screen.getByText('methods.iframe.bridgeMissing')).toBeInTheDocument();
    expect(screen.queryByText('methods.iframe.bridgeLabel')).not.toBeInTheDocument();
    unmount();

    await renderLive(withMethod(EmbedType.IFRAME, { gtmContainerId: 'GTM-ABC1234' }));
    expect(screen.getByText('methods.iframe.bridgeLabel')).toBeInTheDocument();
  });

  it('no longer offers the bare /embed/donate iframe URL, which could not relay any event', async () => {
    await renderLive(withMethod(EmbedType.IFRAME));
    expect(screen.queryByText(/embed\/donate/)).not.toBeInTheDocument();
  });

  it('puts the cookie consent on the association for every method its own page loads GTM for', async () => {
    const { unmount } = await renderLive(withMethod(EmbedType.SCRIPT));
    expect(screen.getByText('tracking.consent.title')).toBeInTheDocument();
    expect(screen.getByText('methods.status.consentYours')).toBeInTheDocument();
    unmount();

    await renderLive(withMethod(EmbedType.DIRECT));
    expect(screen.queryByText('tracking.consent.title')).not.toBeInTheDocument();
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
    await renderLive();
    fireEvent.change(screen.getByPlaceholderText('tracking.placeholder'), { target: { value: 'GTM-ABC1234' } });
    fireEvent.click(screen.getByText('tracking.save'));

    await waitFor(() => expect(mockUpdateLandingConfig).toHaveBeenCalledWith({ gtmContainerId: 'GTM-ABC1234' }));
  });

  it('rejects a malformed GTM container ID without calling the backend', async () => {
    await renderLive();
    // Lowercase — mirrors the backend pattern (UpdateLandingConfigRequest.gtmContainerId).
    fireEvent.change(screen.getByPlaceholderText('tracking.placeholder'), { target: { value: 'GTM-abcd' } });
    fireEvent.click(screen.getByText('tracking.save'));

    expect(screen.getByText('tracking.error')).toBeInTheDocument();
    expect(mockUpdateLandingConfig).not.toHaveBeenCalled();
  });

  it('sends an empty value to clear a configured GTM container ID', async () => {
    await renderLive({ ...liveProfile, gtmContainerId: 'GTM-ABC1234' });
    fireEvent.change(screen.getByDisplayValue('GTM-ABC1234'), { target: { value: '' } });
    fireEvent.click(screen.getByText('tracking.save'));

    await waitFor(() => expect(mockUpdateLandingConfig).toHaveBeenCalledWith({ gtmContainerId: '' }));
  });

  it('offers the head/body tags only for a method whose host page loads the container', async () => {
    const { unmount } = await renderLive(withMethod(EmbedType.DIRECT, { gtmContainerId: 'GTM-ABC1234' }));
    expect(screen.queryByText('tracking.tags.headLabel')).not.toBeInTheDocument();
    unmount();

    await renderLive(withMethod(EmbedType.SCRIPT, { gtmContainerId: 'GTM-ABC1234' }));
    expect(screen.getAllByText('tracking.tags.headLabel')).toHaveLength(1);
    expect(screen.getAllByText('tracking.tags.bodyLabel')).toHaveLength(1);
  });

  it('offers no head/body tags without a configured container', async () => {
    await renderLive(withMethod(EmbedType.SCRIPT));
    expect(screen.queryByText('tracking.tags.headLabel')).not.toBeInTheDocument();
  });

  it('lists the event parameters for a developer', async () => {
    await renderLive();
    expect(screen.getByText('tracking.params.bothTitle')).toBeInTheDocument();
    expect(screen.getByText('tracking.params.checkoutTitle')).toBeInTheDocument();
    expect(screen.getByText('embed_type')).toBeInTheDocument();
  });

  it('requires GTM for the standalone export, without fetching anything', async () => {
    await renderLive(withMethod(EmbedType.HTML));
    expect(screen.getByText('methods.html.gtmMissing')).toBeInTheDocument();
    expect(screen.getByText('methods.status.gtmRequired')).toBeInTheDocument();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('generates no export while another method is selected, even with GTM configured', async () => {
    await renderLive(withMethod(EmbedType.SCRIPT, { gtmContainerId: 'GTM-ABC1234' }));
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('fetches the export from /api/gtm-export once the method is selected and GTM is set', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ html: '<!DOCTYPE html><html><body>real landing content</body></html>' }),
    });

    await renderLive(withMethod(EmbedType.HTML, { gtmContainerId: 'GTM-ABC1234' }));

    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('/api/gtm-export/clk_abc?gtmId=GTM-ABC1234'));
    await waitFor(() => screen.getByText('methods.html.download'));
    expect(screen.getByText('methods.html.file')).toBeInTheDocument();
  });

  it('shows an explicit error and offers a retry when the export fails', async () => {
    mockFetch.mockRejectedValue(new Error('boom'));

    await renderLive(withMethod(EmbedType.HTML, { gtmContainerId: 'GTM-ABC1234' }));

    await waitFor(() => screen.getByText('methods.html.failed'));
    expect(screen.queryByText('methods.html.download')).not.toBeInTheDocument();

    mockFetch.mockResolvedValue({ ok: true, json: async () => ({ html: '<!DOCTYPE html><html><body>ok</body></html>' }) });
    fireEvent.click(screen.getByText('methods.html.reload'));

    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(2));
    await waitFor(() => screen.getByText('methods.html.download'));
  });
});
