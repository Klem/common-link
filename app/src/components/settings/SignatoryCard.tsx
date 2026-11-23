'use client';

import { useEffect, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { SignatureDrawPad } from '@/components/settings/SignatureDrawPad';
import { processSignatureFile, SignatureImageError, SignatureImageErrorCode } from '@/lib/signatureImage';
import { SIGNATORY_LIMITS, SignatureMode } from '@/types/signatory';
import type { SaveSignatoryRequest, SignatoryDto } from '@/types/signatory';

const KEY = 'association.profile.mandate.signatory';

const MODE_OPTIONS: readonly { mode: SignatureMode; labelKey: string }[] = [
  { mode: SignatureMode.UPLOAD, labelKey: `${KEY}.modes.upload` },
  { mode: SignatureMode.DRAWN, labelKey: `${KEY}.modes.drawn` },
];

const IMAGE_ERROR_KEYS: Record<SignatureImageErrorCode, string> = {
  [SignatureImageErrorCode.UNSUPPORTED_TYPE]: `${KEY}.errors.unsupportedType`,
  [SignatureImageErrorCode.UNREADABLE]: `${KEY}.errors.unreadable`,
  [SignatureImageErrorCode.TOO_LARGE]: `${KEY}.errors.tooLarge`,
};

/** Object URL of a blob for an `<img>` preview, revoked when the blob changes or on unmount. */
function useObjectUrl(blob: Blob | null): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blob) {
      setUrl(null);
      return;
    }
    const objectUrl = URL.createObjectURL(blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);
  return url;
}

/** Long date in the app locale [locale] (e.g. "20 novembre 2026"), or an em dash when absent. */
function formatDate(iso: string | null, locale: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(locale, { day: '2-digit', month: 'long', year: 'numeric' });
}

/** DOM id of the card, targeted by the "complete the signer" link of the mandate signing step. */
export const SIGNATORY_CARD_ID = 'signatory-card';

interface SignatoryCardProps {
  /** Saved signer, or null when none is saved yet (or the read failed). */
  signatory: SignatoryDto | null;
  /** Saved signature image, re-sent when the signer is re-saved in the same mode without a new image. */
  savedImage: Blob | null;
  /** Preview URL of {@link savedImage}. */
  savedImageUrl: string | null;
  /** Persists the signer; resolves false when the API refused it. */
  onSave: (request: SaveSignatoryRequest) => Promise<boolean>;
}

/**
 * "Authorised signer of the fiscal receipts" card: name, role and signature image (uploaded or
 * hand-drawn — an image is always required), saved only with the mandatory attestation.
 * Client-side checks mirror the backend `PUT /api/association/me/signatory` validation.
 * The parent remounts the card after each save (key on `updatedAt`), which resets the attestation.
 */
export function SignatoryCard({ signatory, savedImage, savedImageUrl, onSave }: SignatoryCardProps) {
  const t = useTranslations('dashboard');
  const locale = useLocale();

  const [signerName, setSignerName] = useState(signatory?.signerName ?? '');
  const [signerRole, setSignerRole] = useState(signatory?.signerRole ?? '');
  const [mode, setMode] = useState<SignatureMode>(signatory?.mode ?? SignatureMode.UPLOAD);
  const [uploadImage, setUploadImage] = useState<Blob | null>(null);
  const [uploadError, setUploadError] = useState<SignatureImageErrorCode | null>(null);
  const [drawnImage, setDrawnImage] = useState<Blob | null>(null);
  const [attested, setAttested] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [saveFailed, setSaveFailed] = useState(false);

  const uploadPreviewUrl = useObjectUrl(uploadImage);

  const trimmedName = signerName.trim();
  const trimmedRole = signerRole.trim();
  // Without a new image, the saved one is kept — only when it was produced in the selected mode.
  const reusableImage = signatory?.mode === mode ? savedImage : null;
  const newImage = mode === SignatureMode.UPLOAD ? uploadImage : drawnImage;
  const image: Blob | null = newImage ?? reusableImage;

  const isValid =
    trimmedName.length > 0 &&
    trimmedName.length <= SIGNATORY_LIMITS.NAME_MAX &&
    trimmedRole.length > 0 &&
    trimmedRole.length <= SIGNATORY_LIMITS.ROLE_MAX &&
    image !== null;
  const canSave = isValid && attested && !isSaving;

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    setUploadError(null);
    try {
      setUploadImage(await processSignatureFile(file));
    } catch (error) {
      setUploadImage(null);
      setUploadError(error instanceof SignatureImageError ? error.code : SignatureImageErrorCode.UNREADABLE);
    }
  };

  const handleSave = async () => {
    if (!canSave || image === null) return;
    setIsSaving(true);
    setSaveFailed(false);
    const ok = await onSave({ signerName: trimmedName, signerRole: trimmedRole, mode, file: image });
    setIsSaving(false);
    setAttested(false);
    if (!ok) setSaveFailed(true);
  };

  const isSaved = signatory?.mode != null;

  return (
    <div className="card no-hover sig-card" id={SIGNATORY_CARD_ID}>
      <div className="card-h">
        <h3>{t(`${KEY}.title`)}</h3>
        <span className={`set-tab-badge ${isSaved ? 'ok' : 'pending'}`}>
          {isSaved ? t(`${KEY}.badgeSaved`) : t(`${KEY}.badgeMissing`)}
        </span>
      </div>
      <div className="card-b">
        <p className="sig-intro">{t(`${KEY}.intro`)}</p>

        {isSaved && signatory && (
          <div className="sig-saved">
            <span className="fl">{t(`${KEY}.savedTitle`)}</span>
            <div className="sig-preview">
              {savedImageUrl && (
                // eslint-disable-next-line @next/next/no-img-element -- blob: URL, next/image cannot optimise it
                <img src={savedImageUrl} alt={t(`${KEY}.savedAlt`)} />
              )}
            </div>
            <div className="fhint">{t(`${KEY}.savedAt`, { date: formatDate(signatory.updatedAt, locale) })}</div>
          </div>
        )}

        <div className="frow">
          <div className="fg">
            <label htmlFor="signatory-name" className="fl">
              {t('association.profile.signerName')}<span className="req">*</span>{' '}
              <span className="tip" data-tip={t('association.profile.signerNameTip')}>?</span>
            </label>
            <input
              id="signatory-name"
              type="text"
              className="fi"
              maxLength={SIGNATORY_LIMITS.NAME_MAX}
              placeholder={t('association.profile.signerNamePlaceholder')}
              value={signerName}
              onChange={(e) => setSignerName(e.target.value)}
            />
          </div>
          <div className="fg">
            <label htmlFor="signatory-role" className="fl">
              {t('association.profile.signerRole')}<span className="req">*</span>{' '}
              <span className="tip" data-tip={t('association.profile.signerRoleTip')}>?</span>
            </label>
            <input
              id="signatory-role"
              type="text"
              className="fi"
              maxLength={SIGNATORY_LIMITS.ROLE_MAX}
              placeholder={t('association.profile.signerRolePlaceholder')}
              value={signerRole}
              onChange={(e) => setSignerRole(e.target.value)}
            />
          </div>
        </div>

        <div className="fg">
          <span className="fl" id="signature-mode-label">
            {t(`${KEY}.modeLabel`)}<span className="req">*</span>
          </span>
          <div className="sig-modes" role="radiogroup" aria-labelledby="signature-mode-label">
            {MODE_OPTIONS.map((opt) => (
              <label key={opt.mode} className={`sig-mode${mode === opt.mode ? ' active' : ''}`}>
                <input
                  type="radio"
                  name="signature-mode"
                  className="sr-only"
                  value={opt.mode}
                  checked={mode === opt.mode}
                  onChange={() => setMode(opt.mode)}
                />
                {t(opt.labelKey)}
              </label>
            ))}
          </div>

          {mode === SignatureMode.UPLOAD && (
            <div>
              <label className="upload sig-upload">
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  className="sr-only"
                  aria-label={t(`${KEY}.upload.inputLabel`)}
                  onChange={(e) => {
                    void handleFile(e.target.files?.[0]);
                    e.target.value = '';
                  }}
                />
                <p>
                  <b>{t(`${KEY}.upload.cta`)}</b>
                </p>
                <small>{t(`${KEY}.upload.hint`)}</small>
              </label>
              {uploadError && <div className="upload-error" role="alert">{t(IMAGE_ERROR_KEYS[uploadError])}</div>}
              {uploadPreviewUrl && (
                <div className="sig-preview sig-preview-new">
                  {/* eslint-disable-next-line @next/next/no-img-element -- blob: URL, next/image cannot optimise it */}
                  <img src={uploadPreviewUrl} alt={t(`${KEY}.previewAlt`)} />
                </div>
              )}
            </div>
          )}

          {mode === SignatureMode.DRAWN && (
            <div>
              <div className="fhint sig-mode-hint">{t(`${KEY}.draw.hint`)}</div>
              <SignatureDrawPad
                onChange={setDrawnImage}
                padLabel={t(`${KEY}.draw.padLabel`)}
                clearLabel={t(`${KEY}.draw.clear`)}
              />
            </div>
          )}

          {reusableImage && !newImage && <div className="fhint">{t(`${KEY}.keepSaved`)}</div>}
        </div>

        <div className="alert alert-info sig-help">{t(`${KEY}.help`)}</div>

        <label className="sig-attest">
          <input type="checkbox" checked={attested} onChange={(e) => setAttested(e.target.checked)} />
          <span>{t(`${KEY}.attestation`)}</span>
        </label>

        {saveFailed && <div className="fhint error sig-save-error" role="alert">{t(`${KEY}.errors.saveFailed`)}</div>}

        <div className="frow-actions">
          <button type="button" className="btn btn-primary btn-sm" disabled={!canSave} onClick={handleSave}>
            {t(`${KEY}.save`)}
          </button>
        </div>
      </div>
    </div>
  );
}
