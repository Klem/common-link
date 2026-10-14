'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { updateWidgetConfig } from '@/lib/api/association';
import { useToastStore } from '@/stores/toastStore';
import type { AssociationProfileDto } from '@/types/association';

/**
 * Mirror of the backend check on `UpdateWidgetConfigRequest.widgetAllowedOrigin` (rule 8): an
 * absolute http(s) URL with a host, at most 2048 characters. Empty is valid — it clears the setting.
 */
const CAMPAIGN_PAGE_URL_MAX_LENGTH = 2048;

export function isValidCampaignPageUrl(value: string): boolean {
  if (!value) return true;
  if (value.length > CAMPAIGN_PAGE_URL_MAX_LENGTH) return false;
  try {
    const url = new URL(value);
    return (url.protocol === 'http:' || url.protocol === 'https:') && !!url.hostname;
  } catch {
    return false;
  }
}

interface Props {
  profile: AssociationProfileDto | null;
  /** Re-fetches the profile: the embed cards show the saved URL. */
  onProfileChanged: () => Promise<void>;
}

/**
 * Block 2 of the Diffusion tab — the URL of the page where the association publishes its campaign
 * (`widgetAllowedOrigin`): donors come back there after payment, and that is where `purchase` is sent.
 */
export function CampaignPageUrlCard({ profile, onProfileChanged }: Props) {
  const t = useTranslations('settings.diffusion');
  const { addToast } = useToastStore();

  const [url, setUrl] = useState<string>(profile?.widgetAllowedOrigin ?? '');
  const [isSaving, setIsSaving] = useState(false);
  const [urlError, setUrlError] = useState(false);

  useEffect(() => {
    setUrl(profile?.widgetAllowedOrigin ?? '');
  }, [profile?.widgetAllowedOrigin]);

  const handleSave = async () => {
    const trimmed = url.trim();
    if (!isValidCampaignPageUrl(trimmed)) {
      setUrlError(true);
      return;
    }
    setUrlError(false);
    setIsSaving(true);
    try {
      await updateWidgetConfig({ widgetAllowedOrigin: trimmed || null });
      addToast('success', 'widgetAllowedOriginSaved');
      await onProfileChanged();
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="card no-hover" style={{ marginBottom: 24 }}>
      <div className="card-h">
        <h3>{t('pageUrl.title')}</h3>
      </div>
      <div className="card-b">
        <div className="fg">
          <label className="fl">{t('pageUrl.label')}</label>
          <p className="fhint" style={{ marginBottom: 6 }}>{t('pageUrl.hint')}</p>
          <input
            type="url"
            className="fi"
            value={url}
            onChange={(e) => {
              setUrl(e.target.value);
              setUrlError(false);
            }}
            placeholder={t('pageUrl.placeholder')}
            disabled={isSaving}
          />
          {urlError && <p className="fhint error">{t('pageUrl.error')}</p>}
        </div>
        <div className="frow-actions">
          <button type="button" className="btn btn-primary btn-sm" onClick={handleSave} disabled={isSaving}>
            {t('pageUrl.save')}
          </button>
        </div>
      </div>
    </div>
  );
}
