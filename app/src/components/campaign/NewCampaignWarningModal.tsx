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
      <div className="mod" style={{ maxWidth: '520px' }}>
        <div className="mod-h">
          <h3>{t('title')}</h3>
          <button className="mod-x" onClick={onClose}>✕</button>
        </div>

        <div className="mod-b">
          <div className="pp-row boost" style={{ marginBottom: '18px' }}>
            <div className="pp-row-ic">i</div>
            <div className="pp-row-lbl">{t('notVerified')}</div>
          </div>
          <p style={{ fontSize: '14px', color: 'var(--slate-lavender)', lineHeight: 1.55, marginBottom: '14px' }}>
            {t('body')}
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px' }}>
            <div className="pp-row ok">
              <div className="pp-row-ic">🚀</div>
              <div className="pp-row-lbl">{t('autoPublic')}</div>
            </div>
          </div>
          <p style={{ fontSize: '13px', color: 'var(--slate-lavender)', fontStyle: 'italic' }}>
            {t('hint')}
          </p>
        </div>

        <div className="mod-f" style={{ display: 'flex', justifyContent: 'space-between', gap: '10px', flexWrap: 'wrap' }}>
          <button className="btn btn-secondary" onClick={onGoVerify}>
            {t('verify')}
          </button>
          <div style={{ display: 'flex', gap: '10px' }}>
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
