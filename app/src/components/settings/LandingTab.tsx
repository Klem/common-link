'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { getCampaigns } from '@/lib/api/campaign';
import { CampaignStatus } from '@/types/campaign';
import { CopyableCode } from './CopyableCode';
import type { AssociationProfileDto } from '@/types/association';
import type { CampaignSummaryDto } from '@/types/campaign';

interface LandingTabProps {
  profile: AssociationProfileDto | null;
  /**
   * Navigates to the Widget tab, sole owner of the diffusion token and destination
   * campaign — this tab never mutates them.
   */
  onGoToWidget: () => void;
}

/**
 * Fixed height of the JavaScript-free fallback snippet: without `landing.js` there is no
 * channel to report the real page height, so the host has to reserve a generous one.
 */
const IFRAME_FALLBACK_HEIGHT = 2600;

/**
 * Landing page settings tab — read-only.
 *
 * The landing page consumes the very same `widgetToken` and `widgetDestinationCampaignId`
 * as the embedded widget, so both prerequisites are mirrored here without any write path:
 * two editable copies of one field would let an association regenerate the token from this
 * tab and silently break its live widget embed.
 */
export function LandingTab({ profile, onGoToWidget }: LandingTabProps) {
  const t = useTranslations('settings.landing');

  const [campaigns, setCampaigns] = useState<CampaignSummaryDto[]>([]);

  useEffect(() => {
    getCampaigns().then(setCampaigns).catch(() => {});
  }, []);

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

  const copyLabel = t('snippet.copy');
  const copiedLabel = t('snippet.copied');

  return (
    <div>
      {/* ── Prérequis (lecture seule) ─────────────────────────────────── */}
      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{t('prereq.title')}</h3>
        </div>
        <div className="card-b">
          <p className="fhint" style={{ marginBottom: 12 }}>{t('prereq.hint')}</p>

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

      {/* ── Landing page ──────────────────────────────────────────────── */}
      <div className="card no-hover">
        <div className="card-h">
          <h3>{t('snippet.title')}</h3>
        </div>
        <div className="card-b">
          {!widgetToken && <p className="fhint">{t('snippet.noToken')}</p>}
          {widgetToken && !isDestinationLive && (
            <p className="fhint">{t('snippet.noLiveCampaign')}</p>
          )}
          {showSnippets && (
            <>
              <div className="fg" style={{ marginBottom: 20 }}>
                <label className="fl">{t('snippet.url')}</label>
                <p className="fhint" style={{ marginBottom: 6 }}>{t('snippet.urlHelp')}</p>
                <CopyableCode value={landingUrl} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              </div>
              <div className="fg" style={{ marginBottom: 20 }}>
                <label className="fl">{t('snippet.script')}</label>
                <p className="fhint" style={{ marginBottom: 6 }}>{t('snippet.scriptHelp')}</p>
                <CopyableCode value={scriptCode} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              </div>
              <div className="fg">
                <label className="fl">{t('snippet.iframe')}</label>
                <p className="fhint" style={{ marginBottom: 6 }}>{t('snippet.iframeHelp')}</p>
                <CopyableCode value={iframeCode} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
