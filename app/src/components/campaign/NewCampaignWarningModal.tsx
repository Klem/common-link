'use client';

import { useTranslations } from 'next-intl';

interface NewCampaignWarningModalProps {
  onClose: () => void;
  onContinue: () => void;
  onGoVerify: () => void;
}

export function NewCampaignWarningModal({
  onClose,
  onContinue,
  onGoVerify,
}: NewCampaignWarningModalProps) {
  const t = useTranslations('dashboard.campaigns.newCampaignWarn');

  return (
    <div className="ov on" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="mod mod-sm">
        <div className="mod-h">
          <h3>{t('title')}</h3>
          <button className="mod-x" onClick={onClose}>✕</button>
        </div>

        <div className="mod-b">
          <div className="pp-row boost ncw-notice">
            <div className="pp-row-ic">i</div>
            <div className="pp-row-lbl">{t('notVerified')}</div>
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
          <button className="btn btn-secondary" onClick={onGoVerify}>
            {t('verify')}
          </button>
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
