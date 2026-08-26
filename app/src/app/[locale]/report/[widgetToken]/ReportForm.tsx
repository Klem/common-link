'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { reportCampaign } from '@/lib/api/public';

interface Props {
  widgetToken: string;
}

/**
 * Standalone page counterpart to `lp/[widgetToken]/ReportCampaignModal`. The static HTML export
 * (`/api/gtm-export`) strips every script from the embedded landing page, so the in-page modal
 * has no JS to open it there — this page is what the exported footer link points to instead: a
 * real Next.js route, fully hydrated regardless of the host page it was linked from.
 */
export function ReportForm({ widgetToken }: Props) {
  const t = useTranslations('landing.report');

  const [message, setMessage] = useState('');
  const [reporterEmail, setReporterEmail] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);

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

  if (isSubmitted) {
    return (
      <div className="text-center">
        <p className="font-display font-bold text-lg mb-2">{t('title')}</p>
        <p className="text-text-2">{t('success')}</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit}>
      <h1 className="font-display font-bold text-lg mb-2">{t('title')}</h1>
      <p className="text-sm text-text-2 mb-4">{t('description')}</p>
      <div className="form-group">
        <label className="form-label" htmlFor="report-message">
          {t('messageLabel')} <span className="required">*</span>
        </label>
        <textarea
          id="report-message"
          className="form-input"
          rows={4}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder={t('messagePlaceholder')}
          maxLength={4000}
          required
        />
      </div>
      <div className="form-group">
        <label className="form-label" htmlFor="report-email">
          {t('emailLabel')}
        </label>
        <input
          id="report-email"
          className="form-input"
          type="email"
          value={reporterEmail}
          onChange={(e) => setReporterEmail(e.target.value)}
          placeholder={t('emailPlaceholder')}
        />
        <p className="form-hint">{t('emailHint')}</p>
      </div>
      {hasError && <p className="form-error">{t('error')}</p>}
      <button
        type="submit"
        className="btn btn-coral btn-sm"
        disabled={isSubmitting || !message.trim()}
      >
        {isSubmitting ? t('submitting') : t('submit')}
      </button>
    </form>
  );
}
