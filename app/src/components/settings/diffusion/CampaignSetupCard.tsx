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
 * Block 1 of the Diffusion tab, in one card — the campaign every diffusion collects for (only LIVE
 * campaigns are offered), and the diffusion link token that every code carries (regenerating or
 * disabling it breaks the diffusions already published).
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
  // Only a LIVE campaign can collect donations, so it is the only one offered as a destination.
  // A destination that has since left LIVE stays resolved above, so the warning still shows.
  const liveCampaigns = campaigns.filter((c) => c.status === CampaignStatus.LIVE);

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
    <div className="card no-hover" style={{ marginBottom: 24 }}>
      <div className="card-h">
        <h3>{t('campaign.title')}</h3>
      </div>
      <div className="card-b dif-setup">
        <div className="fg">
          <label className="fl" htmlFor="dif-campaign">{t('campaign.label')}</label>
          <div className="dif-inline">
            <select
              id="dif-campaign"
              className="fi"
              value={selectedCampaignId}
              onChange={(e) => handleCampaignChange(e.target.value)}
              disabled={isSavingCampaign}
            >
              <option value="">{t('campaign.placeholder')}</option>
              {liveCampaigns.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.emoji} {c.name}
                </option>
              ))}
            </select>
            {isDestinationLive && <span className="badge badge-active">{t('campaign.live')}</span>}
          </div>
          {selectedCampaign && !isDestinationLive && (
            <p className="fhint error">{t('campaign.warningNotLive', { name: selectedCampaign.name })}</p>
          )}
        </div>

        <div className="fg">
          <span className="fl">{t('token.label')}</span>
          <div className="dif-inline">
            <span className={`badge ${widgetToken ? 'badge-active' : 'badge-neutral'}`}>
              {widgetToken ? t('token.active') : t('token.inactive')}
            </span>
            {widgetToken && (
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={handleGenerateToken}
                disabled={isGeneratingToken}
              >
                {t('token.regenerate')}
              </button>
            )}
            {widgetToken && (
              <button
                type="button"
                className="btn btn-secondary btn-sm dif-btn-danger"
                onClick={handleDeleteToken}
                disabled={isGeneratingToken}
              >
                {t('token.disable')}
              </button>
            )}
            {!widgetToken && (
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleGenerateToken}
                disabled={isGeneratingToken}
              >
                {t('token.generate')}
              </button>
            )}
          </div>
        </div>

        {widgetToken && <p className="fhint dif-setup-warning">{t('token.warning')}</p>}
      </div>
    </div>
  );
}
