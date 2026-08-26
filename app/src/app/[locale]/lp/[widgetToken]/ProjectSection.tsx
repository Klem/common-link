'use client';

import { useLocale, useTranslations } from 'next-intl';

interface ProjectSectionProps {
  campaignName: string;
  campaignDescription: string | null;
  campaignImpactGoals: string | null;
  goal: number;
  currency: string;
  /** ISO 8601 (YYYY-MM-DD), null when the association left the calendrier unset. */
  startDate: string | null;
  endDate: string | null;
}

export function ProjectSection({
  campaignName,
  campaignDescription,
  campaignImpactGoals,
  goal,
  currency,
  startDate,
  endDate,
}: ProjectSectionProps) {
  const t = useTranslations('landing');
  const locale = useLocale();

  const formattedGoal = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(goal);

  const formatDate = (iso: string) =>
    new Intl.DateTimeFormat(locale, { dateStyle: 'long' }).format(new Date(`${iso}T00:00:00`));

  let calendarLabel: string | null = null;
  if (startDate && endDate) {
    calendarLabel = t('project.calendarFull', { start: formatDate(startDate), end: formatDate(endDate) });
  } else if (startDate) {
    calendarLabel = t('project.calendarFrom', { start: formatDate(startDate) });
  } else if (endDate) {
    calendarLabel = t('project.calendarUntil', { end: formatDate(endDate) });
  }

  return (
    <section className="lp-section lp-project">
      <div className="lp-container">
        <span className="lp-eyebrow">{t('project.eyebrow')}</span>
        <h2 className="lp-section-title">{campaignName}</h2>
        {campaignDescription && (
          <p className="lp-project-description">{campaignDescription}</p>
        )}
        <div className="lp-project-facts">
          <div className="lp-project-fact">
            <span className="lp-project-fact-label">{t('project.goalLabel')}</span>
            <span className="lp-project-fact-value">{formattedGoal}</span>
          </div>
          {calendarLabel && (
            <div className="lp-project-fact">
              <span className="lp-project-fact-label">{t('project.calendarLabel')}</span>
              <span className="lp-project-fact-value">{calendarLabel}</span>
            </div>
          )}
        </div>
        {campaignImpactGoals && (
          <div className="lp-impact-box">
            <p className="lp-impact-title">{t('project.impactTitle')}</p>
            <p className="lp-impact-content">{campaignImpactGoals}</p>
          </div>
        )}
      </div>
    </section>
  );
}
