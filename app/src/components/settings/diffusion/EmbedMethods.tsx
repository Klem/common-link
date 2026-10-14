'use client';

import { useCallback, useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { gtmIframeBridgeScript } from '@/lib/gtm';
import type { AssociationProfileDto } from '@/types/association';
import { CopyableCode } from '../CopyableCode';
import { CopyableTextarea } from './CopyableTextarea';

/**
 * Fixed height of the iframe embed: without `landing.js` there is no channel to report the real
 * page height, so the host has to reserve a generous one.
 */
const IFRAME_FALLBACK_HEIGHT = 2600;

interface Props {
  profile: AssociationProfileDto | null;
  /** Whether the destination campaign is LIVE — the public endpoints refuse anything else. */
  isDestinationLive: boolean;
}

/**
 * Where donors come back after payment, shown in every embed card that publishes on the
 * association's own page: without it, they never come back there and `purchase` is never sent.
 */
function CampaignPageStatus({ url }: { url: string | null }) {
  const t = useTranslations('settings.diffusion');
  return url ? (
    <p className="fhint" style={{ marginTop: 10 }}>{t('methods.pageUrlSet', { url })}</p>
  ) : (
    <p className="fhint error" style={{ marginTop: 10 }}>{t('methods.pageUrlMissing')}</p>
  );
}

/**
 * Block 5 of the Diffusion tab — the ways to publish the collection, one card each; the association
 * picks one. Every code below carries the diffusion token of block 1.
 */
export function EmbedMethods({ profile, isDestinationLive }: Props) {
  const t = useTranslations('settings.diffusion');

  const [exportHtml, setExportHtml] = useState<string | null>(null);
  const [exportFailed, setExportFailed] = useState(false);
  const [exportCopied, setExportCopied] = useState(false);

  const widgetToken = profile?.widgetToken ?? null;
  const gtmContainerId = profile?.gtmContainerId ?? null;
  const campaignPageUrl = profile?.widgetAllowedOrigin ?? null;

  const frontUrl = typeof window !== 'undefined' ? window.location.origin : '';
  // Locale pinned to "fr" — mirrors `landing.js` and `widget.js`, both French-only for now.
  const landingUrl = widgetToken ? `${frontUrl}/fr/lp/${widgetToken}` : '';
  const scriptCode = widgetToken
    ? `<script src="${frontUrl}/landing.js" data-widget-token="${widgetToken}" async></script>`
    : '';
  const iframeCode = widgetToken
    ? `<iframe src="${landingUrl}" width="100%" height="${IFRAME_FALLBACK_HEIGHT}" style="border:0" title="${t('methods.iframe.frameTitle')}"></iframe>`
    : '';
  // The iframe embed has no CommonLink loader on the host page to relay `begin_checkout` and `purchase`.
  const bridgeCode = widgetToken ? `<script>${gtmIframeBridgeScript(frontUrl, widgetToken)}</script>` : '';
  const widgetCode = widgetToken
    ? `<script src="${frontUrl}/widget.js" data-widget-token="${widgetToken}" async></script>`
    : '';

  // 1:1 mirror of the public endpoints' 404 (no token) / 409 (destination not LIVE) — never
  // advertise a code that cannot render.
  const showMethods = !!widgetToken && isDestinationLive;
  const showGtmOnly = showMethods && !!gtmContainerId;

  /**
   * Fetches a fresh standalone export from `/api/gtm-export/{widgetToken}` — a snapshot of the real
   * landing page at generation time, not a live view. Re-run on demand ("Régénérer") rather than
   * cached indefinitely, so a later content or GTM ID change doesn't leave a stale download.
   */
  const loadExport = useCallback(async () => {
    if (!widgetToken || !gtmContainerId) return;
    setExportFailed(false);
    setExportHtml(null);
    try {
      const res = await fetch(`/api/gtm-export/${widgetToken}?gtmId=${encodeURIComponent(gtmContainerId)}`);
      if (!res.ok) throw new Error('export failed');
      const { html } = await res.json();
      setExportHtml(html);
    } catch {
      setExportFailed(true);
    }
  }, [widgetToken, gtmContainerId]);

  useEffect(() => {
    if (showGtmOnly) {
      loadExport();
    } else {
      setExportHtml(null);
      setExportFailed(false);
    }
  }, [showGtmOnly, loadExport]);

  const copyExport = async (html: string) => {
    await navigator.clipboard.writeText(html);
    setExportCopied(true);
    setTimeout(() => setExportCopied(false), 2000);
  };

  const downloadExport = (html: string) => {
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

  const copyLabel = t('methods.copy');
  const copiedLabel = t('methods.copied');

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>{t('methods.title')}</h3>
      {!widgetToken && <p className="fhint">{t('methods.noToken')}</p>}
      {widgetToken && !isDestinationLive && <p className="fhint">{t('methods.noLiveCampaign')}</p>}
      {showMethods && (
        <>
          {/* Direct link — the landing page hosted by CommonLink */}
          <div className="card no-hover" style={{ marginBottom: 24 }}>
            <div className="card-h">
              <h3>{t('methods.direct.title')}</h3>
            </div>
            <div className="card-b">
              <p className="fhint" style={{ marginBottom: 6 }}>{t('methods.direct.help')}</p>
              <CopyableCode value={landingUrl} copyLabel={copyLabel} copiedLabel={copiedLabel} />
            </div>
          </div>

          {/* Full landing page — script */}
          <div className="card no-hover" style={{ marginBottom: 24 }}>
            <div className="card-h">
              <h3>{t('methods.script.title')}</h3>
            </div>
            <div className="card-b">
              <label className="fl">{t('methods.script.helpTitle')}</label>
              <p className="fhint" style={{ marginBottom: 6 }}>{t('methods.script.helpDetail')}</p>
              <CopyableCode value={scriptCode} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              <CampaignPageStatus url={campaignPageUrl} />
            </div>
          </div>

          {/* Full landing page — iframe + bridge script */}
          <div className="card no-hover" style={{ marginBottom: 24 }}>
            <div className="card-h">
              <h3>{t('methods.iframe.title')}</h3>
            </div>
            <div className="card-b">
              <label className="fl">{t('methods.iframe.helpTitle')}</label>
              <p className="fhint" style={{ marginBottom: 6 }}>{t('methods.iframe.helpDetail')}</p>
              <CopyableCode value={iframeCode} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              {gtmContainerId && (
                <CopyableTextarea
                  value={bridgeCode}
                  rows={3}
                  label={t('methods.iframe.bridgeLabel')}
                  hint={t('methods.iframe.bridgeHint')}
                  copyLabel={copyLabel}
                  copiedLabel={copiedLabel}
                />
              )}
              <CampaignPageStatus url={campaignPageUrl} />
            </div>
          </div>

          {/* Standalone HTML export */}
          {showGtmOnly && (
            <div className="card no-hover" style={{ marginBottom: 24 }}>
              <div className="card-h">
                <h3>{t('methods.export.title')}</h3>
              </div>
              <div className="card-b">
                <p className="fhint" style={{ marginBottom: 6 }}>{t('methods.export.hint')}</p>
                {exportFailed && <p className="fhint error">{t('methods.export.failed')}</p>}
                {!exportFailed && !exportHtml && <p className="fhint">{t('methods.export.loading')}</p>}
                {exportHtml && (
                  <textarea
                    className="fi"
                    readOnly
                    rows={8}
                    value={exportHtml}
                    data-testid="gtm-export-textarea"
                    style={{ fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }}
                    onFocus={(e) => e.target.select()}
                  />
                )}
                <div className="frow-actions" style={{ marginTop: 6 }}>
                  {exportHtml && (
                    <>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => copyExport(exportHtml)}>
                        {exportCopied ? copiedLabel : copyLabel}
                      </button>
                      <button type="button" className="btn btn-primary btn-sm" onClick={() => downloadExport(exportHtml)}>
                        {t('methods.export.download')}
                      </button>
                    </>
                  )}
                  <button type="button" className="btn btn-secondary btn-sm" onClick={loadExport}>
                    {t('methods.export.reload')}
                  </button>
                </div>
                <CampaignPageStatus url={campaignPageUrl} />
              </div>
            </div>
          )}

          {/* Donation form only — widget.js */}
          <div className="card no-hover" style={{ marginBottom: 24 }}>
            <div className="card-h">
              <h3>{t('methods.widget.title')}</h3>
            </div>
            <div className="card-b">
              <p className="fhint" style={{ marginBottom: 6 }}>{t('methods.widget.help')}</p>
              <p className="fhint" style={{ marginBottom: 6 }}>{t('methods.widget.gtmHint')}</p>
              <CopyableCode value={widgetCode} copyLabel={copyLabel} copiedLabel={copiedLabel} />
              <CampaignPageStatus url={campaignPageUrl} />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
