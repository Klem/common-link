'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { reportCampaign } from '@/lib/api/public';

interface Props {
  isOpen: boolean;
  widgetToken: string;
  onClose: () => void;
}

/**
 * Public "report this campaign" popup (IC-44). No account required — reporting works with or
 * without one. Opening (or reusing) a compliance alert is internal only and has no visible effect
 * for the reporter or the association at submission time; the confirmation only states that the
 * report was received.
 */
export function ReportCampaignModal({ isOpen, widgetToken, onClose }: Props) {
  const t = useTranslations('landing');

  const [message, setMessage] = useState('');
  const [reporterEmail, setReporterEmail] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isOpen, onClose]);

  useEffect(() => {
    if (isOpen) return;
    setMessage('');
    setReporterEmail('');
    setHasError(false);
    setIsSubmitted(false);
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setHasError(false);
    try {
      await reportCampaign(widgetToken, {
        message,
        reporterEmail: reporterEmail.trim() || undefined,
      });
      setIsSubmitted(true);
    } catch {
      setHasError(true);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{t('report.title')}</h3>
          <button className="modal-close" onClick={onClose} aria-label={t('report.close')}>×</button>
        </div>
        <div className="modal-body">
          {isSubmitted ? (
            <p>{t('report.success')}</p>
          ) : (
            <form onSubmit={handleSubmit}>
              <p style={{ marginBottom: 16 }}>{t('report.description')}</p>
              <div className="form-group">
                <label className="form-label" htmlFor="report-message">
                  {t('report.messageLabel')} <span className="required">*</span>
                </label>
                <textarea
                  id="report-message"
                  className="form-input"
                  rows={4}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder={t('report.messagePlaceholder')}
                  maxLength={4000}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="report-email">
                  {t('report.emailLabel')}
                </label>
                <input
                  id="report-email"
                  className="form-input"
                  type="email"
                  value={reporterEmail}
                  onChange={(e) => setReporterEmail(e.target.value)}
                  placeholder={t('report.emailPlaceholder')}
                />
                <p className="form-hint">{t('report.emailHint')}</p>
              </div>
              {hasError && <p className="form-error">{t('report.error')}</p>}
            </form>
          )}
        </div>
        <div className="modal-footer">
          {isSubmitted ? (
            <button onClick={onClose} className="btn btn-primary btn-sm">
              {t('report.closeButton')}
            </button>
          ) : (
            <>
              <button onClick={onClose} className="btn btn-ghost btn-sm">
                {t('report.cancel')}
              </button>
              <button
                onClick={handleSubmit}
                className="btn btn-coral btn-sm"
                disabled={isSubmitting || !message.trim()}
              >
                {isSubmitting ? t('report.submitting') : t('report.submit')}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
