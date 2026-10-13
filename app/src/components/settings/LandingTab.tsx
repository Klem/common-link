'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { getCampaigns } from '@/lib/api/campaign';
import {
  updateLandingConfig,
  uploadLandingLogo,
  deleteLandingLogo,
} from '@/lib/api/association';
import { useToastStore } from '@/stores/toastStore';
import { CampaignStatus } from '@/types/campaign';
import { LandingTheme, LANDING_THEMES } from '@/types/association';
import { apiUrl } from '@/lib/api';
import { GTM_ID_PATTERN, gtmHeadScript, gtmIframeBridgeScript, gtmNoscriptIframe } from '@/lib/gtm';
import { CopyableCode } from './CopyableCode';
import { LandingPreviewModal } from './LandingPreviewModal';
import type { AssociationProfileDto, UpdateLandingConfigRequest } from '@/types/association';
import type { CampaignSummaryDto } from '@/types/campaign';

interface LandingTabProps {
  profile: AssociationProfileDto | null;
  /**
   * Navigates to the Widget tab, sole owner of the diffusion token and destination
   * campaign — this tab never mutates them.
   */
  onGoToWidget: () => void;
  /** Called after the landing configuration changed so the parent hook re-fetches the profile. */
  onConfigChanged: () => Promise<void>;
}

/** Mirrored from the backend (`AssociationLandingService`) — rule 8: same limits both sides. */
const MAX_LOGO_SIZE = 2 * 1024 * 1024;
const LOGO_ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Fixed height of the JavaScript-free fallback snippet: without `landing.js` there is no
 * channel to report the real page height, so the host has to reserve a generous one.
 */
const IFRAME_FALLBACK_HEIGHT = 2600;

/** Read-only code field with its own copy button and transient "copied" state. */
function CopyableTextarea({ value, rows, label, hint, copyLabel, copiedLabel }: {
  value: string;
  rows: number;
  label: string;
  hint?: string;
  copyLabel: string;
  copiedLabel: string;
}) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <>
      <label className="fl" style={{ marginTop: 8 }}>{label}</label>
      {hint && <p className="fhint" style={{ marginBottom: 4 }}>{hint}</p>}
      <textarea
        className="fi"
        readOnly
        rows={rows}
        value={value}
        style={{ fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }}
        onFocus={(e) => e.target.select()}
      />
      <div className="frow-actions" style={{ marginTop: 4 }}>
        <button type="button" className="btn btn-secondary btn-sm" onClick={copy}>
          {copied ? copiedLabel : copyLabel}
        </button>
      </div>
    </>
  );
}

/**
 * Reminder shown in every embed card: the association must enter, in the Widget tab, the URL of the
 * page where it publishes the code — the post-payment return target, and so where `purchase` is sent.
 */
function ReturnReminder({ onGoToWidget }: { onGoToWidget: () => void }) {
  const t = useTranslations('settings.landing');
  return (
    <>
      <p className="fhint" style={{ marginTop: 12, marginBottom: 6 }}>{t('snippet.returnReminder')}</p>
      <div className="frow-actions">
        <button type="button" className="btn btn-secondary btn-sm" onClick={onGoToWidget}>
          {t('prereq.goToWidget')}
        </button>
      </div>
    </>
  );
}

/**
 * Landing page settings tab — read-only.
 *
 * The landing page consumes the very same `widgetToken` and `widgetDestinationCampaignId`
 * as the embedded widget, so both prerequisites are mirrored here without any write path:
 * two editable copies of one field would let an association regenerate the token from this
 * tab and silently break its live widget embed.
 */
export function LandingTab({ profile, onGoToWidget, onConfigChanged }: LandingTabProps) {
  const t = useTranslations('settings.landing');
  const { addToast } = useToastStore();

  const [campaigns, setCampaigns] = useState<CampaignSummaryDto[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [showPreview, setShowPreview] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [gtmInput, setGtmInput] = useState(profile?.gtmContainerId ?? '');
  const [gtmError, setGtmError] = useState(false);
  const [gtmExportCopied, setGtmExportCopied] = useState(false);
  const [gtmExportHtml, setGtmExportHtml] = useState<string | null>(null);
  const [gtmExportFailed, setGtmExportFailed] = useState(false);

  useEffect(() => {
    getCampaigns().then(setCampaigns).catch(() => {});
  }, []);

  useEffect(() => {
    setGtmInput(profile?.gtmContainerId ?? '');
  }, [profile?.gtmContainerId]);

  /**
   * Sends one field per interaction and refreshes the profile — no Save button to understand.
   * The backend leaves every field it did not receive untouched.
   */
  const patchConfig = async (data: UpdateLandingConfigRequest) => {
    setIsSaving(true);
    try {
      await updateLandingConfig(data);
      await onConfigChanged();
      addToast('success', 'landingConfigSaved');
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsSaving(false);
    }
  };

  const handleLogoSelected = async (file: File | undefined) => {
    if (!file) return;
    // Mirror of the backend validation so the association gets an instant, explicit reason.
    if (!LOGO_ALLOWED_MIME.includes(file.type)) {
      addToast('error', 'landingLogoType');
      return;
    }
    if (file.size > MAX_LOGO_SIZE) {
      addToast('error', 'landingLogoSize');
      return;
    }
    setIsSaving(true);
    try {
      await uploadLandingLogo(file);
      await onConfigChanged();
      addToast('success', 'landingLogoSaved');
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsSaving(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleSaveGtm = async () => {
    const trimmed = gtmInput.trim();
    // Mirror of the backend pattern (UpdateLandingConfigRequest.gtmContainerId) — rule 8.
    if (trimmed && !GTM_ID_PATTERN.test(trimmed)) {
      setGtmError(true);
      return;
    }
    setGtmError(false);
    await patchConfig({ gtmContainerId: trimmed });
  };

  const copyToClipboard = async (text: string, setCopied: (copied: boolean) => void) => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadGtmExport = (html: string) => {
    const blob = new Blob([html], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'landing-gtm.html';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleLogoDelete = async () => {
    setIsSaving(true);
    try {
      await deleteLandingLogo();
      await onConfigChanged();
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsSaving(false);
    }
  };

  const widgetToken = profile?.widgetToken ?? null;
  const campaign = campaigns.find((c) => c.id === profile?.widgetDestinationCampaignId) ?? null;
  const isDestinationLive = campaign?.status === CampaignStatus.LIVE;

  const frontUrl = typeof window !== 'undefined' ? window.location.origin : '';
  // Locale pinned to "fr" — mirrors the widget snippet and `landing.js`, both French-only for now.
  const landingUrl = widgetToken ? `${frontUrl}/fr/lp/${widgetToken}` : '';
  const scriptCode = widgetToken
    ? `<script src="${frontUrl}/landing.js" data-widget-token="${widgetToken}" async></script>`
    : '';
  const iframeCode = widgetToken
    ? `<iframe src="${landingUrl}" width="100%" height="${IFRAME_FALLBACK_HEIGHT}" style="border:0" title="${t('iframeTitle')}"></iframe>`
    : '';
  // Same gate as WidgetTab, and a 1:1 mirror of the public endpoint's 404 (no token) / 409
  // (destination not LIVE) — never advertise a link that cannot render.
  const showSnippets = !!widgetToken && isDestinationLive;
  const showGtmExport = showSnippets && !!profile?.gtmContainerId;
  const gtmContainerId = profile?.gtmContainerId ?? null;

  // Embedded through `landing.js` or the iframe bridge, the landing page does not load GTM itself
  // (it would double every event) and relays its events to the host page instead — so these tags
  // on the association's own page are the only GTM instance. Offered as plain copy/paste — same
  // builders the standalone export already uses.
  const gtmHeadTag = gtmContainerId ? `<script>${gtmHeadScript(gtmContainerId)}</script>` : '';
  const gtmBodyTag = gtmContainerId ? `<noscript>${gtmNoscriptIframe(gtmContainerId)}</noscript>` : '';
  // Raw-iframe embed only: it has no CommonLink loader on the host page to relay `begin_checkout` and `purchase`.
  const gtmBridgeTag = widgetToken ? `<script>${gtmIframeBridgeScript(frontUrl, widgetToken)}</script>` : '';

  /**
   * Fetches a fresh standalone export from `/api/gtm-export/{widgetToken}` — a snapshot of the
   * real landing page at generation time, not a live view. Re-run on demand ("Régénérer") rather
   * than cached indefinitely, so a later content or GTM ID change doesn't leave a stale download.
   */
  const loadGtmExport = useCallback(async () => {
    if (!widgetToken || !gtmContainerId) return;
    setGtmExportFailed(false);
    setGtmExportHtml(null);
    try {
      const res = await fetch(`/api/gtm-export/${widgetToken}?gtmId=${encodeURIComponent(gtmContainerId)}`);
      if (!res.ok) throw new Error('export failed');
      const { html } = await res.json();
      setGtmExportHtml(html);
    } catch {
      setGtmExportFailed(true);
    }
  }, [widgetToken, gtmContainerId]);

  useEffect(() => {
    if (showGtmExport) {
      loadGtmExport();
    } else {
      setGtmExportHtml(null);
      setGtmExportFailed(false);
    }
  }, [showGtmExport, loadGtmExport]);

  const copyLabel = t('snippet.copy');
  const copiedLabel = t('snippet.copied');
  const currentTheme = profile?.landingTheme ?? LandingTheme.DEFAULT;

  return (
    <div>
      {/* ── Prérequis (lecture seule) ─────────────────────────────────── */}
      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{t('prereq.title')}</h3>
        </div>
        <div className="card-b">
          <p className="fhint" style={{ marginBottom: 8 }}>{t('prereq.hint')}</p>

          <div className="lt-prereq-row">
            <span className="lt-prereq-label">{t('prereq.token')}</span>
            <span className={`set-tab-badge${widgetToken ? ' ok' : ''}`}>
              {widgetToken ? t('prereq.tokenActive') : t('prereq.tokenInactive')}
            </span>
          </div>
          {widgetToken && (
            <p className="fhint lt-token">{widgetToken}</p>
          )}

          <div className="lt-prereq-row">
            <span className="lt-prereq-label">{t('prereq.campaign')}</span>
            <span className="lt-prereq-value">
              {campaign ? `${campaign.emoji} ${campaign.name}` : t('prereq.campaignNone')}
            </span>
          </div>
          {campaign && !isDestinationLive && (
            <p className="fhint error">{t('prereq.campaignNotLive')}</p>
          )}

          <div className="frow-actions">
            <button type="button" className="btn btn-secondary btn-sm" onClick={onGoToWidget}>
              {t('prereq.goToWidget')}
            </button>
          </div>
        </div>
      </div>

      {/* ── Apparence ─────────────────────────────────────────────────── */}
      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{t('appearance.title')}</h3>
        </div>
        <div className="card-b">
          <div className="fg" style={{ marginBottom: 16 }}>
            <label className="fl">{t('appearance.theme')}</label>
            <p className="fhint" style={{ marginBottom: 6 }}>{t('appearance.themeHint')}</p>
            <div className="lt-theme-grid">
              {LANDING_THEMES.map((theme) => (
                <button
                  key={theme}
                  type="button"
                  className={`lt-theme-card${currentTheme === theme ? ' active' : ''}`}
                  data-theme={theme}
                  onClick={() => patchConfig({ theme })}
                  disabled={isSaving}
                  aria-pressed={currentTheme === theme}
                >
                  <span className="lt-theme-swatches" aria-hidden="true">
                    <span className="lt-swatch lt-swatch-primary" />
                    <span className="lt-swatch lt-swatch-secondary" />
                    <span className="lt-swatch lt-swatch-soft" />
                  </span>
                  <span className="lt-theme-name">{t(`appearance.themes.${theme}`)}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="fg">
            <label className="fl">{t('appearance.logo')}</label>
            <p className="fhint" style={{ marginBottom: 6 }}>{t('appearance.logoHint')}</p>
            {profile?.landingLogo ? (
              <div className="lt-logo-row">
                {/* eslint-disable-next-line @next/next/no-img-element -- served by the API, not /public */}
                <img
                  className="lt-logo-preview"
                  src={apiUrl(profile.landingLogo)}
                  alt={profile.name}
                />
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={handleLogoDelete}
                  disabled={isSaving}
                >
                  {t('appearance.logoRemove')}
                </button>
              </div>
            ) : (
              <p className="fhint">{t('appearance.logoNone')}</p>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept={LOGO_ALLOWED_MIME.join(',')}
              className="lt-logo-input"
              onChange={(e) => handleLogoSelected(e.target.files?.[0])}
              disabled={isSaving}
            />
          </div>
        </div>
      </div>

      {/* ── Google Tag Manager ────────────────────────────────────────── */}
      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{t('gtm.title')}</h3>
        </div>
        <div className="card-b">
          <p className="fhint" style={{ marginBottom: 8 }}>{t('gtm.hint')}</p>
          <div className="fg" style={{ marginBottom: 12 }}>
            <label className="fl">{t('gtm.label')}</label>
            <input
              type="text"
              className="fi"
              value={gtmInput}
              onChange={(e) => {
                setGtmInput(e.target.value);
                setGtmError(false);
              }}
              placeholder={t('gtm.placeholder')}
              disabled={isSaving}
            />
            {gtmError && <p className="fhint error">{t('gtm.error')}</p>}
            <div className="frow-actions" style={{ marginTop: 6 }}>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleSaveGtm}
                disabled={isSaving}
              >
                {t('gtm.save')}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Landing page : one card per integration type ──────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
        <h3>{t('snippet.title')}</h3>
        {widgetToken && (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setShowPreview(true)}>
            {t('preview.open')}
          </button>
        )}
      </div>
      {!widgetToken && <p className="fhint">{t('snippet.noToken')}</p>}
      {widgetToken && !isDestinationLive && <p className="fhint">{t('snippet.noLiveCampaign')}</p>}
      {showSnippets && (
        <>
          {/* Direct link */}
          <div className="card no-hover" style={{ marginBottom: 24 }}>
            <div className="card-h">
              <h3>{t('snippet.url')}</h3>
            </div>
            <div className="card-b">
              <p className="fhint" style={{ marginBottom: 6 }}>{t('snippet.urlHelp')}</p>
              <CopyableCode value={landingUrl} copyLabel={copyLabel} copiedLabel={copiedLabel} />
            </div>
          </div>

          {/* Google Tag Manager on the association's page — shared by the script and iframe cards */}
          {showGtmExport && (
            <div className="card no-hover" style={{ marginBottom: 24 }}>
              <div className="card-h">
                <h3>{t('gtm.embed.title')}</h3>
              </div>
              <div className="card-b">
                <p className="fhint" style={{ marginBottom: 6 }}>{t('gtm.embed.hint')}</p>
                <CopyableTextarea value={gtmHeadTag} rows={3} label={t('gtm.embed.headLabel')} copyLabel={copyLabel} copiedLabel={copiedLabel} />
                <CopyableTextarea value={gtmBodyTag} rows={2} label={t('gtm.embed.bodyLabel')} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              </div>
            </div>
          )}

          {/* Script snippet */}
          <div className="card no-hover" style={{ marginBottom: 24 }}>
            <div className="card-h">
              <h3>{t('snippet.script')}</h3>
            </div>
            <div className="card-b">
              <label className="fl">{t('snippet.scriptHelpTitle')}</label>
              <p className="fhint" style={{ marginBottom: 6 }}>{t('snippet.scriptHelpDetail')}</p>
              <CopyableCode value={scriptCode} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              <ReturnReminder onGoToWidget={onGoToWidget} />
            </div>
          </div>

          {/* Raw iframe */}
          <div className="card no-hover" style={{ marginBottom: 24 }}>
            <div className="card-h">
              <h3>{t('snippet.iframe')}</h3>
            </div>
            <div className="card-b">
              <label className="fl">{t('snippet.iframeHelpTitle')}</label>
              <p className="fhint" style={{ marginBottom: 6 }}>{t('snippet.iframeHelpDetail')}</p>
              <CopyableCode value={iframeCode} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              {showGtmExport && (
                <CopyableTextarea
                  value={gtmBridgeTag}
                  rows={3}
                  label={t('gtm.embed.bridgeLabel')}
                  hint={t('gtm.embed.bridgeHint')}
                  copyLabel={copyLabel}
                  copiedLabel={copiedLabel}
                />
              )}
              <ReturnReminder onGoToWidget={onGoToWidget} />
            </div>
          </div>

          {/* Standalone HTML */}
          {showGtmExport && (
            <div className="card no-hover" style={{ marginBottom: 24 }}>
              <div className="card-h">
                <h3>{t('gtm.export.title')}</h3>
              </div>
              <div className="card-b">
                <p className="fhint" style={{ marginBottom: 6 }}>{t('gtm.export.hint')}</p>
                {gtmExportFailed && <p className="fhint error">{t('gtm.export.failed')}</p>}
                {!gtmExportFailed && !gtmExportHtml && <p className="fhint">{t('gtm.export.loading')}</p>}
                {gtmExportHtml && (
                  <textarea
                    className="fi"
                    readOnly
                    rows={8}
                    value={gtmExportHtml}
                    data-testid="gtm-export-textarea"
                    style={{ fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }}
                    onFocus={(e) => e.target.select()}
                  />
                )}
                <div className="frow-actions" style={{ marginTop: 6 }}>
                  {gtmExportHtml && (
                    <>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => copyToClipboard(gtmExportHtml, setGtmExportCopied)}
                      >
                        {gtmExportCopied ? copiedLabel : copyLabel}
                      </button>
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        onClick={() => handleDownloadGtmExport(gtmExportHtml)}
                      >
                        {t('gtm.export.download')}
                      </button>
                    </>
                  )}
                  <button type="button" className="btn btn-secondary btn-sm" onClick={loadGtmExport}>
                    {t('gtm.export.reload')}
                  </button>
                </div>
                <ReturnReminder onGoToWidget={onGoToWidget} />
              </div>
            </div>
          )}
        </>
      )}

      {showPreview && widgetToken && (
        <LandingPreviewModal widgetToken={widgetToken} onClose={() => setShowPreview(false)} />
      )}
    </div>
  );
}
