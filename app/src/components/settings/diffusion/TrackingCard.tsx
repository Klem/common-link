'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { GTM_ID_PATTERN, gtmHeadScript, gtmNoscriptIframe } from '@/lib/gtm';
import type { AssociationProfileDto } from '@/types/association';
import { CopyableTextarea } from './CopyableTextarea';
import { useLandingConfigPatch } from './useLandingConfigPatch';

interface Props {
  profile: AssociationProfileDto | null;
  onProfileChanged: () => Promise<void>;
}

/**
 * Block 3 of the Diffusion tab — the association's GTM container, and its official head/body tags
 * for its own page. Once embedded, the CommonLink page loads no GTM itself and relays its events
 * (`begin_checkout`, `purchase`) to the host page, so the container must be installed there.
 */
export function TrackingCard({ profile, onProfileChanged }: Props) {
  const t = useTranslations('settings.diffusion');
  const { isSaving, patchConfig } = useLandingConfigPatch(onProfileChanged);

  const [gtmInput, setGtmInput] = useState(profile?.gtmContainerId ?? '');
  const [gtmError, setGtmError] = useState(false);

  useEffect(() => {
    setGtmInput(profile?.gtmContainerId ?? '');
  }, [profile?.gtmContainerId]);

  const handleSave = async () => {
    const trimmed = gtmInput.trim();
    // Mirror of the backend pattern (UpdateLandingConfigRequest.gtmContainerId) — rule 8.
    if (trimmed && !GTM_ID_PATTERN.test(trimmed)) {
      setGtmError(true);
      return;
    }
    setGtmError(false);
    await patchConfig({ gtmContainerId: trimmed });
  };

  const gtmContainerId = profile?.gtmContainerId ?? null;
  const copyLabel = t('methods.copy');
  const copiedLabel = t('methods.copied');

  return (
    <div className="card no-hover" style={{ marginBottom: 24 }}>
      <div className="card-h">
        <h3>{t('tracking.title')}</h3>
      </div>
      <div className="card-b">
        <p className="fhint" style={{ marginBottom: 8 }}>{t('tracking.hint')}</p>
        <div className="fg" style={{ marginBottom: 12 }}>
          <label className="fl">{t('tracking.label')}</label>
          <input
            type="text"
            className="fi"
            value={gtmInput}
            onChange={(e) => {
              setGtmInput(e.target.value);
              setGtmError(false);
            }}
            placeholder={t('tracking.placeholder')}
            disabled={isSaving}
          />
          {gtmError && <p className="fhint error">{t('tracking.error')}</p>}
          <div className="frow-actions" style={{ marginTop: 6 }}>
            <button type="button" className="btn btn-primary btn-sm" onClick={handleSave} disabled={isSaving}>
              {t('tracking.save')}
            </button>
          </div>
        </div>

        {gtmContainerId && (
          <>
            <label className="fl">{t('tracking.tags.title')}</label>
            <p className="fhint" style={{ marginBottom: 6 }}>{t('tracking.tags.hint')}</p>
            <CopyableTextarea
              value={`<script>${gtmHeadScript(gtmContainerId)}</script>`}
              rows={3}
              label={t('tracking.tags.headLabel')}
              copyLabel={copyLabel}
              copiedLabel={copiedLabel}
            />
            <CopyableTextarea
              value={`<noscript>${gtmNoscriptIframe(gtmContainerId)}</noscript>`}
              rows={2}
              label={t('tracking.tags.bodyLabel')}
              copyLabel={copyLabel}
              copiedLabel={copiedLabel}
            />
          </>
        )}
      </div>
    </div>
  );
}
