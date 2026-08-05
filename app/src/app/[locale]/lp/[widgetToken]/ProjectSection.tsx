'use client';

import { useTranslations } from 'next-intl';

interface ProjectSectionProps {
  campaignName: string;
  campaignDescription: string | null;
  campaignImpactGoals: string | null;
}

export function ProjectSection({
  campaignName,
  campaignDescription,
  campaignImpactGoals,
}: ProjectSectionProps) {
  const t = useTranslations('landing');

  return (
    <section className="lp-section lp-project">
      <div className="lp-container">
        <span className="lp-eyebrow">{t('project.eyebrow')}</span>
        <h2 className="lp-section-title">{campaignName}</h2>
        {campaignDescription && (
          <p className="lp-project-description">{campaignDescription}</p>
        )}
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
