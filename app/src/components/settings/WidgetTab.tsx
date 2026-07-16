'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { getCampaigns } from '@/lib/api/campaign';
import { generateWidgetToken, deleteWidgetToken, updateAssociationProfile } from '@/lib/api/association';
import { useToastStore } from '@/stores/toastStore';
import { CampaignStatus } from '@/types/campaign';
import type { AssociationProfileDto } from '@/types/association';
import type { CampaignSummaryDto } from '@/types/campaign';

interface WidgetTabProps {
  profile: AssociationProfileDto | null;
  /** Called after token generation/deletion so the parent hook re-fetches the full profile. */
  onTokenChanged: () => Promise<void>;
}

export function WidgetTab({ profile, onTokenChanged }: WidgetTabProps) {
  const t = useTranslations('settings.widget');
  const { addToast } = useToastStore();

  const [campaigns, setCampaigns] = useState<CampaignSummaryDto[]>([]);
  const [selectedCampaignId, setSelectedCampaignId] = useState<string>(
    profile?.widgetDestinationCampaignId ?? '',
  );
  const [widgetToken, setWidgetToken] = useState<string | null>(profile?.widgetToken ?? null);
  const [isSavingCampaign, setIsSavingCampaign] = useState(false);
  const [isGeneratingToken, setIsGeneratingToken] = useState(false);
  const [copiedSnippet, setCopiedSnippet] = useState(false);
  const [copiedIframe, setCopiedIframe] = useState(false);

  useEffect(() => {
    getCampaigns().then(setCampaigns).catch(() => {});
  }, []);

  // Sync token from parent profile when profile reloads (e.g. after refreshProfile)
  useEffect(() => {
    setWidgetToken(profile?.widgetToken ?? null);
    setSelectedCampaignId(profile?.widgetDestinationCampaignId ?? '');
  }, [profile?.widgetToken, profile?.widgetDestinationCampaignId]);

  const selectedCampaign = campaigns.find((c) => c.id === selectedCampaignId) ?? null;
  const isDestinationLive = selectedCampaign?.status === CampaignStatus.LIVE;
  const frontUrl = typeof window !== 'undefined' ? window.location.origin : '';
  const snippetCode = widgetToken
    ? `<script src="${frontUrl}/widget.js" data-widget-token="${widgetToken}" async></script>`
    : '';
  const iframeUrl = widgetToken ? `${frontUrl}/fr/embed/donate/${widgetToken}` : '';
  const showSnippet = !!widgetToken && isDestinationLive;

  const handleCampaignChange = async (campaignId: string) => {
    setSelectedCampaignId(campaignId);
    setIsSavingCampaign(true);
    try {
      await updateAssociationProfile({ widgetDestinationCampaignId: campaignId || null });
      addToast('success', 'widgetDestinationSaved');
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsSavingCampaign(false);
    }
  };

  const handleGenerateToken = async () => {
    setIsGeneratingToken(true);
    try {
      const { widgetToken: newToken } = await generateWidgetToken();
      setWidgetToken(newToken);
      await onTokenChanged();
      addToast('success', 'widgetTokenGenerated');
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsGeneratingToken(false);
    }
  };

  const handleDeleteToken = async () => {
    setIsGeneratingToken(true);
    try {
      await deleteWidgetToken();
      setWidgetToken(null);
      await onTokenChanged();
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsGeneratingToken(false);
    }
  };

  const copyToClipboard = async (text: string, which: 'snippet' | 'iframe') => {
    await navigator.clipboard.writeText(text);
    if (which === 'snippet') {
      setCopiedSnippet(true);
      setTimeout(() => setCopiedSnippet(false), 2000);
    } else {
      setCopiedIframe(true);
      setTimeout(() => setCopiedIframe(false), 2000);
    }
  };

  return (
    <div>
      {/* ── Campagne de destination ──────────────────────────────────── */}
      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{t('title')}</h3>
        </div>
        <div className="card-b">
          <div className="fg">
            <label className="fl">{t('destination.label')}</label>
            <select
              className="fi"
              value={selectedCampaignId}
              onChange={(e) => handleCampaignChange(e.target.value)}
              disabled={isSavingCampaign}
            >
              <option value="">{t('destination.placeholder')}</option>
              {campaigns.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.emoji} {c.name}
                  {c.status !== CampaignStatus.LIVE ? ` (${c.status})` : ''}
                </option>
              ))}
            </select>
            {selectedCampaignId && !isDestinationLive && (
              <p className="fhint error">{t('destination.warningNotLive')}</p>
            )}
          </div>
        </div>
      </div>

      {/* ── Token ────────────────────────────────────────────────────── */}
      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{t('token.label')}</h3>
          <span className={`set-tab-badge${widgetToken ? ' ok' : ''}`}>
            {widgetToken ? t('token.active') : t('token.inactive')}
          </span>
        </div>
        <div className="card-b">
          {widgetToken && (
            <p className="fhint" style={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
              {widgetToken}
            </p>
          )}
          {widgetToken && (
            <p className="fhint error">{t('token.regenerateWarning')}</p>
          )}
          <div className="frow-actions">
            {widgetToken && (
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={handleDeleteToken}
                disabled={isGeneratingToken}
              >
                {t('token.disable')}
              </button>
            )}
            <button
              type="button"
              className="btn btn-primary btn-sm"
              onClick={handleGenerateToken}
              disabled={isGeneratingToken}
            >
              {widgetToken ? t('token.regenerate') : t('token.generate')}
            </button>
          </div>
        </div>
      </div>

      {/* ── Snippet ──────────────────────────────────────────────────── */}
      <div className="card no-hover">
        <div className="card-h">
          <h3>{t('snippet.label')}</h3>
        </div>
        <div className="card-b">
          {!widgetToken && (
            <p className="fhint">{t('snippet.noToken')}</p>
          )}
          {widgetToken && !isDestinationLive && (
            <p className="fhint">{t('snippet.noLiveCampaign')}</p>
          )}
          {showSnippet && (
            <>
              <div className="fg" style={{ marginBottom: 20 }}>
                <label className="fl">{t('snippet.label')}</label>
                <p className="fhint" style={{ marginBottom: 6 }}>{t('snippet.snippetHelp')}</p>
                <div style={{ position: 'relative' }}>
                  <code
                    className="fi"
                    style={{ fontFamily: 'monospace', fontSize: '12px', overflowX: 'auto', display: 'block', paddingRight: 40 }}
                  >
                    {snippetCode}
                  </code>
                  <button
                    type="button"
                    title={copiedSnippet ? t('snippet.copied') : t('snippet.copy')}
                    onClick={() => copyToClipboard(snippetCode, 'snippet')}
                    style={{ position: 'absolute', top: '50%', right: 8, transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', padding: 4, color: 'var(--slate-lavender)' }}
                  >
                    {copiedSnippet ? (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                    ) : (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                    )}
                  </button>
                </div>
              </div>
              <div className="fg">
                <label className="fl">{t('snippet.iframeUrl')}</label>
                <p className="fhint" style={{ marginBottom: 6 }}>{t('snippet.iframeHelp')}</p>
                <div style={{ position: 'relative' }}>
                  <code
                    className="fi"
                    style={{ fontFamily: 'monospace', fontSize: '12px', display: 'block', paddingRight: 40 }}
                  >
                    {iframeUrl}
                  </code>
                  <button
                    type="button"
                    title={copiedIframe ? t('snippet.copied') : t('snippet.copy')}
                    onClick={() => copyToClipboard(iframeUrl, 'iframe')}
                    style={{ position: 'absolute', top: '50%', right: 8, transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', padding: 4, color: 'var(--slate-lavender)' }}
                  >
                    {copiedIframe ? (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
                    ) : (
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                    )}
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
