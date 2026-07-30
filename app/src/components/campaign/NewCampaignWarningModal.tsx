'use client';

import { useTranslations } from 'next-intl';
import { VerificationStatus } from '@/types/association';

interface NewCampaignWarningModalProps {
  /**
   * KYC lifecycle state of the association profile. The modal is only opened for a non-VERIFIED
   * association, but PENDING, REJECTED and UNVERIFIED say different things and offer different
   * actions — a dossier already under review must not be told to submit one.
   */
  verificationStatus: VerificationStatus;
  onClose: () => void;
  onContinue: () => void;
  onGoVerify: () => void;
}

/** Statuses for which sending the association to the verification page is actionable. */
const ACTIONABLE: VerificationStatus[] = [VerificationStatus.UNVERIFIED, VerificationStatus.REJECTED];

/**
 * Warning shown when a non-verified association creates a campaign: the draft can be written now
 * but only goes public once the dossier is validated.
 */
export function NewCampaignWarningModal({
  verificationStatus,
  onClose,
  onContinue,
  onGoVerify,
}: NewCampaignWarningModalProps) {
  const t = useTranslations('dashboard.campaigns.newCampaignWarn');
  const showVerifyCta = ACTIONABLE.includes(verificationStatus);

  return (
    <div className="ov on" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="mod mod-sm">
        <div className="mod-h">
          <h3>{t('title')}</h3>
          <button className="mod-x" onClick={onClose}>✕</button>
        </div>

        <div className="mod-b">
          <div className="pp-row boost ncw-notice">
            <div className="pp-row-ic">{verificationStatus === VerificationStatus.PENDING ? '⏳' : 'i'}</div>
            <div className="pp-row-lbl">{t(`status.${verificationStatus}`)}</div>
          </div>
          <p className="ncw-body">
            {t('body')}
          </p>
          <div className="ncw-list">
            <div className="pp-row ok">
              <div className="pp-row-ic">🚀</div>
              <div className="pp-row-lbl">{t('autoPublic')}</div>
            </div>
          </div>
          <p className="ncw-hint">
            {t('hint')}
          </p>
        </div>

        <div className="mod-f mod-f-split">
          {showVerifyCta ? (
            <button className="btn btn-secondary" onClick={onGoVerify}>
              {t('verify')}
            </button>
          ) : (
            <span />
          )}
          <div className="ncw-actions">
            <button className="btn btn-secondary" onClick={onClose}>
              {t('cancel')}
            </button>
            <button className="btn btn-primary" onClick={onContinue}>
              {t('continue')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
