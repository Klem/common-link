'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { EmbedType, GTM_ID_PATTERN, gtmHeadScript, gtmNoscriptIframe } from '@/lib/gtm';
import type { AssociationProfileDto } from '@/types/association';
import { CopyableTextarea } from './CopyableTextarea';
import { isGtmLoadedByCommonLink } from './diffusionMethods';
import { useLandingConfigPatch } from './useLandingConfigPatch';

interface Props {
  /** Selected diffusion method: decides who loads the container and what the field says. */
  method: EmbedType;
  profile: AssociationProfileDto | null;
  onProfileChanged: () => Promise<void>;
}

/**
 * The association's GTM container ID, shown in the settings of the selected method. One stored value
 * serves every method; only the explanation changes:
 * - direct link and standalone export: CommonLink loads the container itself (required for the export);
 * - embedded methods: CommonLink relays `begin_checkout` and `purchase` to the host page, which must
 *   load the container and collect cookie consent — the official head/body tags are offered for it.
 */
export function GtmField({ method, profile, onProfileChanged }: Props) {
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
  const onHostPage = !isGtmLoadedByCommonLink(method);
  const required = method === EmbedType.HTML;
  const helpKey = onHostPage ? 'host' : method;
  const copyLabel = t('methods.copy');
  const copiedLabel = t('methods.copied');

  return (
    <div className="fg">
      <label className="fl" htmlFor="dif-gtm">
        {t('tracking.label')}{' '}
        {required ? <span className="req">*</span> : <span className="dif-optional">{t('tracking.optional')}</span>}
      </label>
      <p className="fhint">{t(`tracking.help.${helpKey}`)}</p>
      {onHostPage && (
        <div role="note" className="dif-note warn">
          <strong>{t('tracking.consent.title')}</strong> {t('tracking.consent.text')}
        </div>
      )}
      <div className="dif-inline">
        <input
          id="dif-gtm"
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
        <button type="button" className="btn btn-secondary btn-sm" onClick={handleSave} disabled={isSaving}>
          {t('tracking.save')}
        </button>
      </div>
      {gtmError && <p className="fhint error">{t('tracking.error')}</p>}
      {onHostPage && gtmContainerId && (
        <details className="dif-details">
          <summary>{t('tracking.tags.toggle')}</summary>
          <p className="fhint">{t('tracking.tags.hint')}</p>
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
        </details>
      )}
    </div>
  );
}
