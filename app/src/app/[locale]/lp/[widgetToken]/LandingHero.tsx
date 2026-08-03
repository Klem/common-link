'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { apiUrl } from '@/lib/api';

interface LandingHeroProps {
  campaignName: string;
  campaignCategory: string | null;
  campaignReason: string | null;
  campaignDescription: string | null;
  associationRna: string;
  taxReductionRate: number;
  campaignId: string;
  coverImage: string | null;
}

export function LandingHero({
  campaignName,
  campaignCategory,
  campaignReason,
  campaignDescription,
  associationRna,
  taxReductionRate,
  coverImage,
}: LandingHeroProps) {
  const t = useTranslations('landing');
  const [imgError, setImgError] = useState(false);

  const tagline = campaignReason ?? campaignDescription;

  return (
    <section className="lp-hero">
      <div className="lp-hero-inner">
        <div className="lp-hero-content">
          {campaignCategory && (
            <span className="lp-eyebrow">{campaignCategory}</span>
          )}
          <h1 className="lp-hero-title">{campaignName}</h1>
          {tagline && <p className="lp-hero-tagline">{tagline}</p>}
          <a href="#don" className="lp-hero-cta">
            {t('hero.cta')}
          </a>
          <div className="lp-hero-trust">
            <span>{t('hero.trust.secure')}</span>
            <span>{t('hero.trust.receipt', { rate: taxReductionRate })}</span>
            <span>{t('hero.trust.rna', { rna: associationRna })}</span>
          </div>
        </div>
        <div className="lp-hero-visual">
          {coverImage !== null && !imgError ? (
            <img
              src={apiUrl(coverImage)}
              alt={campaignName}
              className="lp-hero-cover"
              onError={() => setImgError(true)}
            />
          ) : (
            <div className="lp-hero-cover lp-hero-cover--gradient" />
          )}
        </div>
      </div>
    </section>
  );
}
