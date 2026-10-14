'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { generateWidgetToken, deleteWidgetToken, updateAssociationProfile } from '@/lib/api/association';
import { useToastStore } from '@/stores/toastStore';
import { CampaignStatus } from '@/types/campaign';
import type { AssociationProfileDto } from '@/types/association';
import type { CampaignSummaryDto } from '@/types/campaign';

interface Props {
  profile: AssociationProfileDto | null;
  campaigns: CampaignSummaryDto[];
  /** Re-fetches the profile: every other block of the tab depends on the campaign and the token. */
  onProfileChanged: () => Promise<void>;
}

/**
 * Block 1 of the Diffusion tab — the campaign every embed collects for, and the diffusion token
 * that every embed code carries (regenerating it breaks the codes already published).
 */
export function CampaignSetupCard({ profile, campaigns, onProfileChanged }: Props) {
  const t = useTranslations('settings.diffusion');
  const { addToast } = useToastStore();

  const [selectedCampaignId, setSelectedCampaignId] = useState<string>(profile?.widgetDestinationCampaignId ?? '');
  const [widgetToken, setWidgetToken] = useState<string | null>(profile?.widgetToken ?? null);
  const [isSavingCampaign, setIsSavingCampaign] = useState(false);
  const [isGeneratingToken, setIsGeneratingToken] = useState(false);

  useEffect(() => {
    setWidgetToken(profile?.widgetToken ?? null);
    setSelectedCampaignId(profile?.widgetDestinationCampaignId ?? '');
  }, [profile?.widgetToken, profile?.widgetDestinationCampaignId]);

  const selectedCampaign = campaigns.find((c) => c.id === selectedCampaignId) ?? null;
  const isDestinationLive = selectedCampaign?.status === CampaignStatus.LIVE;

  const handleCampaignChange = async (campaignId: string) => {
    setSelectedCampaignId(campaignId);
    setIsSavingCampaign(true);
    try {
      await updateAssociationProfile({ widgetDestinationCampaignId: campaignId || null });
      addToast('success', 'widgetDestinationSaved');
      await onProfileChanged();
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
      await onProfileChanged();
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
      await onProfileChanged();
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsGeneratingToken(false);
    }
  };

  return (
    <>
      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{t('campaign.title')}</h3>
        </div>
        <div className="card-b">
          <div className="fg">
            <label className="fl">{t('campaign.label')}</label>
            <select
              className="fi"
              value={selectedCampaignId}
              onChange={(e) => handleCampaignChange(e.target.value)}
              disabled={isSavingCampaign}
            >
              <option value="">{t('campaign.placeholder')}</option>
              {campaigns.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.emoji} {c.name}
                  {c.status !== CampaignStatus.LIVE ? ` (${c.status})` : ''}
                </option>
              ))}
            </select>
            {selectedCampaignId && !isDestinationLive && (
              <p className="fhint error">{t('campaign.warningNotLive')}</p>
            )}
          </div>
        </div>
      </div>

      <div className="card no-hover" style={{ marginBottom: 24 }}>
        <div className="card-h">
          <h3>{t('token.label')}</h3>
          <span className={`set-tab-badge${widgetToken ? ' ok' : ''}`}>
            {widgetToken ? t('token.active') : t('token.inactive')}
          </span>
        </div>
        <div className="card-b">
          {widgetToken && (
            <>
              <p className="fhint" style={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{widgetToken}</p>
              <p className="fhint error">⚠️ {t('token.regenerateWarning')}</p>
              <p className="fhint error">⚠️ {t('token.disableWarning')}</p>
            </>
          )}
          <div className="frow-actions">
            {widgetToken && (
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                style={{ color: 'var(--error, #dc3545)', borderColor: 'var(--error, #dc3545)' }}
                onClick={handleDeleteToken}
                disabled={isGeneratingToken}
              >
                {t('token.disable')}
              </button>
            )}
            <button
              type="button"
              className="btn btn-primary btn-sm"
              style={widgetToken ? { background: 'var(--error, #dc3545)', borderColor: 'var(--error, #dc3545)' } : undefined}
              onClick={handleGenerateToken}
              disabled={isGeneratingToken}
            >
              {widgetToken ? t('token.regenerate') : t('token.generate')}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
