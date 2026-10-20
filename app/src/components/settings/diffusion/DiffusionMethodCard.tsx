'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import type { EmbedType } from '@/lib/gtm';
import type { AssociationProfileDto } from '@/types/association';
import { MethodPicker } from './MethodPicker';
import { MethodSettings } from './MethodSettings';
import { initialMethod } from './diffusionMethods';
import { useLandingConfigPatch } from './useLandingConfigPatch';

interface Props {
  profile: AssociationProfileDto | null;
  /** Whether the destination campaign is LIVE — the public endpoints refuse anything else. */
  isDestinationLive: boolean;
  onProfileChanged: () => Promise<void>;
}

/**
 * Block 3 of the Diffusion tab — the association picks ONE diffusion method, stored on its profile
 * (`embedType`), and only that method's settings are shown. Nothing is offered until the token
 * exists and the destination is LIVE: never advertise a code that cannot render.
 */
export function DiffusionMethodCard({ profile, isDestinationLive, onProfileChanged }: Props) {
  const t = useTranslations('settings.diffusion');
  const { isSaving, patchConfig } = useLandingConfigPatch(onProfileChanged);
  const [method, setMethod] = useState<EmbedType>(() => initialMethod(profile));

  // Recomputed when the profile first arrives (the tab may mount before it) and whenever the stored
  // pick changes — not on other profile refreshes, so saving a campaign page URL never moves the pick.
  const profileLoaded = !!profile;
  const storedMethod = profile?.embedType ?? null;
  useEffect(() => {
    if (profileLoaded) setMethod(initialMethod(profile));
    // eslint-disable-next-line react-hooks/exhaustive-deps -- see above
  }, [profileLoaded, storedMethod]);

  const pick = (next: EmbedType) => {
    if (next === method) return;
    // Optimistic: the settings switch at once; the profile refresh confirms the stored value.
    setMethod(next);
    patchConfig({ embedType: next });
  };

  const widgetToken = profile?.widgetToken ?? null;
  const showMethods = !!profile && !!widgetToken && isDestinationLive;

  return (
    <section className="card no-hover" aria-labelledby="dif-methods-title">
      <div className="card-h dif-methods-h">
        <h3 id="dif-methods-title">{t('methods.title')}</h3>
        <span className="badge badge-authorised">{t('methods.oneAtATime')}</span>
        <p className="fhint dif-methods-intro">{t('methods.intro')}</p>
      </div>
      <div className="card-b">
        {!widgetToken && <p className="fhint">{t('methods.noToken')}</p>}
        {widgetToken && !isDestinationLive && <p className="fhint">{t('methods.noLiveCampaign')}</p>}
        {showMethods && (
          <>
            <MethodPicker value={method} onChange={pick} disabled={isSaving} />
            <div role="note" className="dif-note warn">
              <strong>{t('methods.onePerPage.title')}</strong> {t('methods.onePerPage.text')}
            </div>
            <MethodSettings method={method} profile={profile} onProfileChanged={onProfileChanged} />
          </>
        )}
      </div>
    </section>
  );
}
