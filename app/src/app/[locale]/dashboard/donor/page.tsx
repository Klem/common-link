'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useTranslations, useLocale } from 'next-intl';
import { useAuthStore } from '@/stores/authStore';
import { EmptyStateCard, Topbar } from '@/components/dashboard';
import { DonorAssociationCard } from '@/components/donor/DonorAssociationCard';
import { DonationJourneyTimeline } from '@/components/donor/DonationJourneyTimeline';
import { DonationTraceabilityModal } from '@/components/donor/DonationTraceabilityModal';
import { RecentActivityBlock } from '@/components/donor/RecentActivityBlock';
import { DonationCta } from '@/components/donor/DonationCta';
import { useDonorStats } from '@/hooks/dashboard/useDonorStats';
import { useDonorDonations } from '@/hooks/dashboard/useDonorDonations';
import { useDonorAssociations } from '@/hooks/dashboard/useDonorAssociations';
import { useDonorRecommendations } from '@/hooks/dashboard/useDonorRecommendations';
import { ROUTES } from '@/lib/routes';
import { useCauseLabel } from '@/lib/campaignCause';

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

export default function DonorDashboardPage() {
  const t = useTranslations('dashboard');
  const causeLabel = useCauseLabel();
  const locale = useLocale();
  const user = useAuthStore((s) => s.user);
  const { stats, isLoading: statsLoading } = useDonorStats();
  const { donationsPage, isLoading: donationsLoading } = useDonorDonations(3);
  const { associations, isLoading: associationsLoading } = useDonorAssociations();
  const { recommendations, isLoading: recommendationsLoading } = useDonorRecommendations();
  const [traceabilityDonationId, setTraceabilityDonationId] = useState<string | null>(null);

  const name = user?.displayName?.trim() || user?.email || '';
  const recentDonations = donationsPage?.content ?? [];
  const recentAssociations = associations.slice(0, 3);
  const topRecommendation = recommendations[0] ?? null;
  const projectsSupported = associations.reduce((sum, a) => sum + a.campaignCount, 0);
  const pct =
    topRecommendation && topRecommendation.goal > 0
      ? Math.round((topRecommendation.raised / topRecommendation.goal) * 100)
      : 0;

  return (
    <div>
      <Topbar title={t('title')} />

      <div className="page">
        <div className="page-head">
          <div>
            <h1>{t('donor.home.greetingTitle', { name })}</h1>
            <p>{t('donor.home.greetingSubtitle')}</p>
          </div>
          <Link href={`/${locale}${ROUTES.DONOR_IMPACT}`} className="btn btn-secondary">
            <span aria-hidden="true">📤</span> {t('donor.home.shareImpact')}
          </Link>
        </div>

        <div className="home-stack">
          {/* Bloc 1 — Le parcours de votre don */}
          {donationsLoading ? (
            <p className="text-sm text-text-2 py-4" aria-live="polite">
              {t('donor.donations.loading')}
            </p>
          ) : (
            <DonationJourneyTimeline
              initialDonationId={recentDonations[0]?.id ?? null}
              knownDonations={recentDonations}
              onOpenTraceability={setTraceabilityDonationId}
            />
          )}

          {/* Bloc 2 — Depuis votre dernière visite */}
          <RecentActivityBlock />

          {/* Bloc 3 — Votre contribution cumulée */}
          <section className="narrative-card" aria-labelledby="narrative-h2">
            <div className="narrative-top">
              <h2 id="narrative-h2">{t('donor.home.narrative.title')}</h2>
              <span className="tag">{t('donor.home.narrative.tag')}</span>
            </div>
            <p className="narrative-text">{t('donor.home.narrative.text', { name })}</p>
            <div className="narrative-grid">
              <div className="narrative-item">
                <div className="narrative-val">{statsLoading ? '—' : fmtEur(stats?.totalDonated ?? 0)}</div>
                <div className="narrative-label">{t('donor.home.narrative.donated')}</div>
              </div>
              <div className="narrative-item">
                <div className="narrative-val">{associationsLoading ? '—' : projectsSupported}</div>
                <div className="narrative-label">{t('donor.home.narrative.projects')}</div>
              </div>
              <div className="narrative-item">
                <div className="narrative-val">{statsLoading ? '—' : (stats?.associationCount ?? 0)}</div>
                <div className="narrative-label">{t('donor.home.narrative.associations')}</div>
              </div>
            </div>
            <div className="narrative-bottom">
              <span className="freshness-tag freshness-tag-light">{t('donor.home.narrative.footer')}</span>
            </div>
          </section>

          {/* Bloc 4 — Un projet suggéré */}
          {recommendationsLoading ? (
            <p className="text-sm text-text-2" aria-live="polite">
              {t('donor.recommendations.loading')}
            </p>
          ) : (
            topRecommendation && (
              <section className="suggest-card" aria-labelledby="suggest-h3">
                <div className="suggest-img">
                  <span aria-hidden="true">{topRecommendation.campaignEmoji}</span>
                  <span className="suggest-img-badge">{t('donor.recommendations.sectionTitle')}</span>
                </div>
                <div className="suggest-body">
                  <span className="suggest-asso">{topRecommendation.associationName}</span>
                  <h3 className="suggest-title" id="suggest-h3">
                    {topRecommendation.campaignName}
                  </h3>
                  {topRecommendation.matchedCategory && (
                    <p className="suggest-rationale">
                      {t('donor.recommendations.reason', { category: causeLabel(topRecommendation.matchedCategory) })}
                    </p>
                  )}
                  <div
                    className="suggest-bar"
                    role="progressbar"
                    aria-valuenow={pct}
                    aria-valuemin={0}
                    aria-valuemax={100}
                  >
                    <div className="suggest-fill" style={{ width: `${pct}%` }} />
                  </div>
                  <div className="suggest-stats">
                    <span>
                      <strong>{fmtEur(topRecommendation.raised)}</strong>{' '}
                      {t('donor.home.suggestion.raisedOf')} {fmtEur(topRecommendation.goal)}
                    </span>
                    <span>{pct}%</span>
                  </div>
                  <div className="suggest-actions">
                    <DonationCta
                      associationName={topRecommendation.associationName}
                      donationUrl={topRecommendation.donationUrl}
                      className="btn btn-sm btn-primary"
                    />
                    <Link href={`/${locale}${ROUTES.DONOR_RECOMMENDATIONS}`} className="btn btn-sm btn-secondary">
                      {t('donor.home.viewMoreProjects')}
                    </Link>
                  </div>
                </div>
              </section>
            )
          )}

          {/* Sections without a maquette equivalent on this page — kept as-is (generic cards),
              not part of L18's scope; see sprint-5 report for a fidelity note. */}
          <div className="card card-no-hover">
            <div className="card-h flex items-center justify-between">
              <span className="font-display font-bold text-sm">{t('donor.sections.recentDonations')}</span>
              <Link href={`/${locale}${ROUTES.DONOR_DONATIONS}`} className="text-sm text-text-2">
                {t('donor.sections.viewAll')}
              </Link>
            </div>
            <div className="card-b">
              {donationsLoading ? (
                <p className="text-sm text-text-2" aria-live="polite">
                  {t('donor.donations.loading')}
                </p>
              ) : recentDonations.length === 0 ? (
                <EmptyStateCard
                  icon={t('donor.empty.icon')}
                  title={t('donor.empty.title')}
                  subtitle={t('donor.empty.subtitle')}
                  actionLabel={t('donor.empty.cta')}
                  actionHref="/dashboard/campaigns"
                />
              ) : (
                <ul className="flex flex-col gap-3">
                  {recentDonations.map((donation) => (
                    <li key={donation.id} className="flex items-center justify-between text-sm">
                      <span>
                        <Link href={`/${locale}${ROUTES.DONOR_CAMPAIGN_REPORT(donation.campaignId)}`}>
                          {donation.campaignEmoji} {donation.campaignName}
                        </Link>{' '}
                        — {donation.associationName}
                      </span>
                      <span className="font-display font-bold">{fmtEur(donation.amount)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div className="card card-no-hover">
            <div className="card-h flex items-center justify-between">
              <span className="font-display font-bold text-sm">{t('donor.sections.myAssociations')}</span>
              <Link href={`/${locale}${ROUTES.DONOR_ASSOCIATIONS}`} className="text-sm text-text-2">
                {t('donor.sections.viewAll')}
              </Link>
            </div>
            <div className="card-b">
              {associationsLoading ? (
                <p className="text-sm text-text-2" aria-live="polite">
                  {t('donor.associations.loading')}
                </p>
              ) : recentAssociations.length === 0 ? (
                <EmptyStateCard
                  icon={t('donor.empty.icon')}
                  title={t('donor.empty.title')}
                  subtitle={t('donor.empty.subtitle')}
                />
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                  {recentAssociations.map((association, i) => (
                    <DonorAssociationCard
                      key={association.associationId}
                      association={association}
                      colorIndex={i}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <DonationTraceabilityModal
        donationId={traceabilityDonationId}
        onClose={() => setTraceabilityDonationId(null)}
      />
    </div>
  );
}
