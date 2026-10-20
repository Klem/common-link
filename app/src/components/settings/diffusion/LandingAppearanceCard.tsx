'use client';

import { useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { uploadLandingLogo, deleteLandingLogo } from '@/lib/api/association';
import { useToastStore } from '@/stores/toastStore';
import { LandingTheme, LANDING_THEMES } from '@/types/association';
import { apiUrl } from '@/lib/api';
import type { AssociationProfileDto } from '@/types/association';
import { LandingPreviewModal } from '../LandingPreviewModal';
import { useLandingConfigPatch } from './useLandingConfigPatch';

/** Mirrored from the backend (`AssociationLandingService`) — rule 8: same limits both sides. */
const MAX_LOGO_SIZE = 2 * 1024 * 1024;
const LOGO_ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];

interface Props {
  profile: AssociationProfileDto | null;
  onProfileChanged: () => Promise<void>;
}

/** Block 4 of the Diffusion tab — theme and logo of the landing page, with its preview. */
export function LandingAppearanceCard({ profile, onProfileChanged }: Props) {
  const t = useTranslations('settings.diffusion');
  const { addToast } = useToastStore();
  const { isSaving, setIsSaving, patchConfig } = useLandingConfigPatch(onProfileChanged);
  const [showPreview, setShowPreview] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const widgetToken = profile?.widgetToken ?? null;
  const currentTheme = profile?.landingTheme ?? LandingTheme.DEFAULT;

  const handleLogoSelected = async (file: File | undefined) => {
    if (!file) return;
    // Mirror of the backend validation so the association gets an instant, explicit reason.
    if (!LOGO_ALLOWED_MIME.includes(file.type)) {
      addToast('error', 'landingLogoType');
      return;
    }
    if (file.size > MAX_LOGO_SIZE) {
      addToast('error', 'landingLogoSize');
      return;
    }
    setIsSaving(true);
    try {
      await uploadLandingLogo(file);
      await onProfileChanged();
      addToast('success', 'landingLogoSaved');
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsSaving(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleLogoDelete = async () => {
    setIsSaving(true);
    try {
      await deleteLandingLogo();
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
        <h3>{t('appearance.title')}</h3>
        {widgetToken && (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setShowPreview(true)}>
            {t('preview.open')}
          </button>
        )}
      </div>
      <div className="card-b">
        <div className="fg" style={{ marginBottom: 16 }}>
          <label className="fl">{t('appearance.theme')}</label>
          <p className="fhint" style={{ marginBottom: 6 }}>{t('appearance.themeHint')}</p>
          <div className="lt-theme-grid">
            {LANDING_THEMES.map((theme) => (
              <button
                key={theme}
                type="button"
                className={`lt-theme-card${currentTheme === theme ? ' active' : ''}`}
                data-theme={theme}
                onClick={() => patchConfig({ theme })}
                disabled={isSaving}
                aria-pressed={currentTheme === theme}
              >
                <span className="lt-theme-swatches" aria-hidden="true">
                  <span className="lt-swatch lt-swatch-primary" />
                  <span className="lt-swatch lt-swatch-secondary" />
                  <span className="lt-swatch lt-swatch-soft" />
                </span>
                <span className="lt-theme-name">{t(`appearance.themes.${theme}`)}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="fg">
          <label className="fl">{t('appearance.logo')}</label>
          <p className="fhint" style={{ marginBottom: 6 }}>{t('appearance.logoHint')}</p>
          {profile?.landingLogo ? (
            <div className="lt-logo-row">
              {/* eslint-disable-next-line @next/next/no-img-element -- served by the API, not /public */}
              <img className="lt-logo-preview" src={apiUrl(profile.landingLogo)} alt={profile.name} />
              <button type="button" className="btn btn-secondary btn-sm" onClick={handleLogoDelete} disabled={isSaving}>
                {t('appearance.logoRemove')}
              </button>
            </div>
          ) : (
            <p className="fhint">{t('appearance.logoNone')}</p>
          )}
          <input
            ref={fileInputRef}
            type="file"
            accept={LOGO_ALLOWED_MIME.join(',')}
            className="lt-logo-input"
            onChange={(e) => handleLogoSelected(e.target.files?.[0])}
            disabled={isSaving}
          />
        </div>
      </div>

      {showPreview && widgetToken && (
        <LandingPreviewModal widgetToken={widgetToken} onClose={() => setShowPreview(false)} />
      )}
    </div>
  );
}
