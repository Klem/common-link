'use client';

import { useTranslations } from 'next-intl';
import { EmbedType, gtmIframeBridgeScript } from '@/lib/gtm';
import type { AssociationProfileDto } from '@/types/association';
import { CopyableCode } from '../CopyableCode';
import { CampaignPageUrlField } from './CampaignPageUrlField';
import { CopyableTextarea } from './CopyableTextarea';
import { GtmField } from './GtmField';
import { HtmlExport } from './HtmlExport';
import { isGtmLoadedByCommonLink } from './diffusionMethods';

/**
 * Fixed height of the iframe embed: without `landing.js` there is no channel to report the real
 * page height, so the host has to reserve a generous one.
 */
const IFRAME_FALLBACK_HEIGHT = 2600;

const StatusTone = {
  OK: 'badge-active',
  NEUTRAL: 'badge-neutral',
  WARN: 'badge-warning',
} as const;
type StatusTone = (typeof StatusTone)[keyof typeof StatusTone];

interface Props {
  method: EmbedType;
  /** Profile with a diffusion token and a LIVE destination — the parent shows nothing otherwise. */
  profile: AssociationProfileDto;
  onProfileChanged: () => Promise<void>;
}

/** One labelled code to copy, with an optional hint under it. */
function CodeBlock({ label, hint, value }: { label: string; hint?: string; value: string }) {
  const t = useTranslations('settings.diffusion');
  return (
    <div className="fg">
      <span className="fl">{label}</span>
      <CopyableCode value={value} copyLabel={t('methods.copy')} copiedLabel={t('methods.copied')} />
      {hint && <p className="fhint">{hint}</p>}
    </div>
  );
}

/**
 * Settings of the selected diffusion method, and only of it: its status, the direct link or the
 * campaign page URL, the code(s) to paste or the HTML export, the GTM container, and the event
 * parameters for a developer. Every code carries the diffusion token of block 1.
 */
export function MethodSettings({ method, profile, onProfileChanged }: Props) {
  const t = useTranslations('settings.diffusion');

  const widgetToken = profile.widgetToken ?? '';
  const gtmContainerId = profile.gtmContainerId ?? null;
  const hasPageUrl = !!profile.widgetAllowedOrigin;
  const isDirect = method === EmbedType.DIRECT;

  const frontUrl = typeof window !== 'undefined' ? window.location.origin : '';
  // Locale pinned to "fr" — mirrors `landing.js` and `widget.js`, both French-only for now.
  const landingUrl = `${frontUrl}/fr/lp/${widgetToken}`;
  const scriptCode = `<script src="${frontUrl}/landing.js" data-widget-token="${widgetToken}" async></script>`;
  const iframeCode = `<iframe src="${landingUrl}" width="100%" height="${IFRAME_FALLBACK_HEIGHT}" style="border:0" title="${t('methods.iframe.frameTitle')}"></iframe>`;
  const widgetCode = `<script src="${frontUrl}/widget.js" data-widget-token="${widgetToken}" async></script>`;

  const statuses: { key: string; tone: StatusTone }[] = [
    { key: 'campaignLive', tone: StatusTone.OK },
    { key: 'linkActive', tone: StatusTone.OK },
  ];
  if (isDirect) statuses.push({ key: 'noReturnUrl', tone: StatusTone.NEUTRAL });
  else statuses.push(hasPageUrl ? { key: 'returnUrlSet', tone: StatusTone.OK } : { key: 'returnUrlMissing', tone: StatusTone.WARN });
  if (method === EmbedType.HTML && !gtmContainerId) statuses.push({ key: 'gtmRequired', tone: StatusTone.WARN });
  if (!isGtmLoadedByCommonLink(method)) statuses.push({ key: 'consentYours', tone: StatusTone.WARN });

  return (
    <div className="dif-settings">
      <div className="dif-settings-h">
        <div className="dif-settings-heading">
          <h4 className="dif-settings-title">{t('methods.settings', { method: t(`methods.${method}.title`) })}</h4>
          <p className="fhint">{t(`methods.${method}.where`)}</p>
        </div>
        <ul className="dif-status" aria-label={t('methods.status.label')}>
          {statuses.map((s) => (
            <li key={s.key} className={`badge ${s.tone}`}>{t(`methods.status.${s.key}`)}</li>
          ))}
        </ul>
      </div>

      <div className="dif-settings-b">
        {isDirect ? (
          <>
            <div className="fg">
              <span className="fl">{t('methods.direct.label')}</span>
              <CopyableCode value={landingUrl} copyLabel={t('methods.copy')} copiedLabel={t('methods.copied')} />
              <div className="dif-inline">
                <a className="btn btn-secondary btn-sm" href={landingUrl} target="_blank" rel="noopener noreferrer">
                  {t('methods.direct.open')}
                </a>
              </div>
              <p className="fhint">{t('methods.direct.help')}</p>
            </div>
            <div role="note" className="dif-note">
              <strong>{t('methods.direct.returnTitle')}</strong> {t('methods.direct.returnText')}
            </div>
          </>
        ) : (
          <CampaignPageUrlField profile={profile} onProfileChanged={onProfileChanged} />
        )}

        {method === EmbedType.SCRIPT && (
          <CodeBlock label={t('methods.script.codeLabel')} hint={t('methods.script.codeHint')} value={scriptCode} />
        )}

        {method === EmbedType.IFRAME && (
          <>
            <CodeBlock label={t('methods.iframe.codeLabel')} hint={t('methods.iframe.codeHint')} value={iframeCode} />
            {/* The iframe embed has no CommonLink loader on the host page to relay `begin_checkout` and `purchase`. */}
            {gtmContainerId ? (
              <div className="fg">
                <CopyableTextarea
                  value={`<script>${gtmIframeBridgeScript(frontUrl, widgetToken)}</script>`}
                  rows={3}
                  label={t('methods.iframe.bridgeLabel')}
                  hint={t('methods.iframe.bridgeHint')}
                  copyLabel={t('methods.copy')}
                  copiedLabel={t('methods.copied')}
                />
              </div>
            ) : (
              <p className="fhint">{t('methods.iframe.bridgeMissing')}</p>
            )}
          </>
        )}

        {method === EmbedType.HTML && <HtmlExport widgetToken={widgetToken} gtmContainerId={gtmContainerId} />}

        {method === EmbedType.WIDGET && (
          <CodeBlock label={t('methods.widget.codeLabel')} hint={t('methods.widget.codeHint')} value={widgetCode} />
        )}

        <GtmField method={method} profile={profile} onProfileChanged={onProfileChanged} />

        {/* Params outside `ecommerce` reach no tag until the association maps them in GTM. */}
        <details className="dif-details dif-details-dev">
          <summary>{t('tracking.params.toggle')}</summary>
          <div className="dif-params">
            <div className="dif-params-box">
              <strong>{t('tracking.params.bothTitle')}</strong>
              <span><code>embed_type</code> : {t('tracking.params.embedType', { embedType: method })}</span>
              <span><code>anonymous</code> : {t('tracking.params.anonymous')}</span>
              <span><code>payment_method</code> : {t('tracking.params.paymentMethod')}</span>
            </div>
            <div className="dif-params-box">
              <strong>{t('tracking.params.checkoutTitle')}</strong>
              <span><code>amount_source</code> : {t('tracking.params.amountSource')}</span>
              <span><code>first_amount</code>, <code>amount_changes</code></span>
              <span><code>utm_*</code> : {t('tracking.params.utm')}</span>
            </div>
            <p className="fhint dif-params-note">{t('tracking.params.howTo')}</p>
          </div>
        </details>
      </div>
    </div>
  );
}
