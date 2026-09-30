'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { EmptyStateCard, StatCard, Topbar } from '@/components/dashboard';
import { ShareImpactModal } from '@/components/donor/ShareImpactModal';
import { useDonorCampaignReport } from '@/hooks/dashboard/useDonorCampaignReport';
import { downloadCampaignReportPdf } from '@/lib/api/donor';
import { useToastStore } from '@/stores/toastStore';
import { MilestoneStatus } from '@/types/campaign';
import type { MilestoneDto } from '@/types/campaign';
import type { ShareableImpact } from '@/lib/impactWording';

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  );
}

function milestoneBadge(status: MilestoneDto['status']): string {
  if (status === MilestoneStatus.REACHED) return '✅';
  if (status === MilestoneStatus.CURRENT) return '⏳';
  return '🔒';
}

/**
 * Donor-facing "bilan de campagne": hero, own contribution, KPIs, milestones, fund usage
 * (planned vs spent, expandable to the confirmed-payout detail), registry banner, PDF export.
 *
 * Vocabulary (D4): the only word used for money actually paid out is "dépensé" — never "engagé",
 * even for a campaign still in progress. This page therefore does NOT reuse the association-side
 * `VarianceTable` component, whose "colActual" column reads "Réalisé": it renders the same
 * `variance.charges` data with donor-scoped i18n keys instead.
 */
export default function DonorCampaignReportPage() {
  const params = useParams();
  const campaignId = params.campaignId as string;
  const t = useTranslations('dashboard.donor.report');
  const { report, isLoading, error } = useDonorCampaignReport(campaignId);
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set());
  const [pdfDownloading, setPdfDownloading] = useState(false);
  const [shareTarget, setShareTarget] = useState<ShareableImpact | null>(null);

  function toggleSection(sectionCode: string): void {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(sectionCode)) {
        next.delete(sectionCode);
      } else {
        next.add(sectionCode);
      }
      return next;
    });
  }

  async function handleDownloadPdf(): Promise<void> {
    if (!report) return;
    setPdfDownloading(true);
    try {
      const blob = await downloadCampaignReportPdf(report.campaignId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `bilan-${report.campaignId}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      useToastStore.getState().addToast('error', 'errors.receiptDownloadFailed');
    } finally {
      setPdfDownloading(false);
    }
  }

  if (isLoading) {
    return (
      <div>
        <Topbar title={t('loading')} />
        <div className="page">
          <p className="text-sm text-text-2 py-8 text-center" aria-live="polite">
            {t('loading')}
          </p>
        </div>
      </div>
    );
  }

  if (error || !report) {
    return (
      <div>
        <Topbar title={t('error')} />
        <div className="page">
          <EmptyStateCard icon="⚠️" title={t('error')} subtitle={t('errorSubtitle')} />
        </div>
      </div>
    );
  }

  const progressPct = report.goal > 0 ? Math.min(100, Math.round((report.raised / report.goal) * 100)) : 0;

  return (
    <div>
      <Topbar title={report.campaignName} />

      <div className="page">
      <div className="flex flex-col gap-6">
      <div className="card card-no-hover">
        <div className="card-b flex flex-col gap-4">
          <div className="flex items-center justify-between flex-wrap gap-2">
            <h1 className="font-display font-black text-2xl md:text-3xl">
              {report.campaignEmoji} {report.campaignName}
            </h1>
            <span className="text-sm text-text-2">{report.associationName}</span>
          </div>
          <div className="progress-bar">
            <div className="progress-fill teal" style={{ width: `${progressPct}%` }} />
          </div>
          <div className="flex items-center justify-between text-sm text-text-2">
            <span>
              {fmtEur(report.raised)} / {fmtEur(report.goal)}
            </span>
            <span>{progressPct}%</span>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard icon="💚" label={t('kpi.contribution')} value={fmtEur(report.donorContribution)} variant="teal" />
        <StatCard icon="🎯" label={t('kpi.raised')} value={fmtEur(report.raised)} variant="indigo" />
        <StatCard icon="💸" label={t('kpi.spent')} value={fmtEur(report.variance.totals.totalActualCharges)} variant="amber" />
      </div>

      <div className="card card-no-hover">
        <div className="card-h">
          <span className="font-display font-bold text-sm">{t('milestones.title')}</span>
        </div>
        <div className="card-b">
          {report.milestones.length === 0 ? (
            <p className="text-sm text-text-2">{t('milestones.empty')}</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {report.milestones.map((milestone) => (
                <li key={milestone.id} className="flex items-center justify-between text-sm">
                  <span>
                    {milestone.emoji} {milestone.title}
                  </span>
                  <span className="text-text-2 text-xs">
                    {milestoneBadge(milestone.status)} {fmtEur(milestone.targetAmount)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="card card-no-hover">
        <div className="card-h">
          <span className="font-display font-bold text-sm">{t('funds.title')}</span>
        </div>
        <div className="card-b flex flex-col gap-4">
          {report.variance.charges.length === 0 ? (
            <p className="text-sm text-text-2">{t('funds.empty')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table aria-label={t('funds.title')}>
                <thead>
                  <tr>
                    <th scope="col">{t('funds.category')}</th>
                    <th scope="col">{t('funds.planned')}</th>
                    <th scope="col">{t('funds.spent')}</th>
                  </tr>
                </thead>
                <tbody>
                  {report.variance.charges.map((section) => (
                    <tr key={section.sectionCode}>
                      <td>{section.sectionName}</td>
                      <td>{fmtEur(section.planned)}</td>
                      <td>{fmtEur(section.actual)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex flex-col gap-3">
            {report.variance.charges.map((section) => {
              const isExpanded = expandedSections.has(section.sectionCode);
              const sectionPayouts = report.confirmedPayouts.filter(
                (payout) => payout.sectionCode === section.sectionCode,
              );
              return (
                <div key={section.sectionCode} className="flex flex-col gap-2">
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm self-start"
                    aria-expanded={isExpanded}
                    aria-label={`${isExpanded ? t('funds.hideDetail') : t('funds.showDetail')} — ${section.sectionName}`}
                    onClick={() => toggleSection(section.sectionCode)}
                  >
                    {section.sectionName} — {isExpanded ? t('funds.hideDetail') : t('funds.showDetail')}
                  </button>

                  {isExpanded && (
                    sectionPayouts.length === 0 ? (
                      <p className="text-sm text-text-2 pl-4">{t('funds.detailEmpty')}</p>
                    ) : (
                      <ul className="flex flex-col gap-2 pl-4">
                        {sectionPayouts.map((payout) => (
                          <li key={payout.payoutId} className="flex items-center justify-between text-sm">
                            <span>
                              {payout.label} — {payout.payeeName}{' '}
                              <span className="text-text-2 text-xs">({fmtDate(payout.confirmedAt)})</span>
                            </span>
                            <span className="font-display font-bold">{fmtEur(payout.amount)}</span>
                          </li>
                        ))}
                      </ul>
                    )
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      <div className="card card-no-hover">
        <div className="card-h flex items-center justify-between">
          <span className="font-display font-bold text-sm">{t('story.title')}</span>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() =>
              setShareTarget({
                campaignId: report.campaignId,
                campaignName: report.campaignName,
                campaignEmoji: report.campaignEmoji,
                associationName: report.associationName,
                storySummary: report.story?.storySummary ?? null,
              })
            }
          >
            {t('story.share')}
          </button>
        </div>
        <div className="card-b">
          {report.story ? (
            // Sanitized server-side (StoryHtmlSanitizer) before ever being persisted — safe to
            // render as-is. Reserved to the donor authenticated and gated on having funded this
            // campaign (DonorReadScope), not a public page.
            <div className="rte-content text-sm leading-relaxed" dangerouslySetInnerHTML={{ __html: report.story.storyText }} />
          ) : (
            <p className="text-sm text-text-2">{t('story.notYetPublished')}</p>
          )}
        </div>
      </div>

      <div className="alert alert-info">
        <span className="alert-icon" aria-hidden="true">
          ℹ️
        </span>
        <div>{report.registryBannerText}</div>
      </div>

      <button
        type="button"
        className="btn btn-primary self-start"
        onClick={handleDownloadPdf}
        disabled={pdfDownloading}
      >
        {t('downloadPdf')}
      </button>

      <ShareImpactModal impact={shareTarget} onClose={() => setShareTarget(null)} />
      </div>
      </div>
    </div>
  );
}
