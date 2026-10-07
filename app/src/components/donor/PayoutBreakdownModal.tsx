'use client';

import { useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';
import { Donut, type DonutSlice } from '@/components/ui/Donut';
import { useDonorPayoutBreakdown } from '@/hooks/dashboard/useDonorPayoutBreakdown';

interface Props {
  campaignId: string | null;
  payoutId: string | null;
  onClose: () => void;
  /** Opens the existing donation traceability modal for one of the donor's own contributing lines. */
  onOpenTraceability: (donationId: string) => void;
}

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  );
}

/**
 * Modal "Voir la répartition" — the inverse of the donation traceability modal: for one payout,
 * which donations funded it (Sprint 6).
 *
 * Privacy: only the viewing donor's own contributing donations are ever listed individually.
 * Every other donor's contribution is a single pie slice / line, and even that aggregate amount is
 * withheld below a minimum contributor count (`othersTotal === null`, distinct from
 * `othersDonationCount === 0` meaning no other donor contributed at all) — see
 * `PayoutFundingBreakdownDto`. Never render a figure for another donor individually.
 */
export function PayoutBreakdownModal({ campaignId, payoutId, onClose, onOpenTraceability }: Props) {
  const t = useTranslations('dashboard.donor.payoutBreakdown');
  const { breakdown, isLoading, error } = useDonorPayoutBreakdown(campaignId, payoutId);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (payoutId === null) return;
    closeButtonRef.current?.focus();

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [payoutId, onClose]);

  if (payoutId === null) return null;

  const slices: DonutSlice[] = breakdown
    ? [
        ...breakdown.myLines.map((line, i) => ({
          label: t('chart.mine', { index: i + 1 }),
          value: line.amount,
          color: 'var(--color-green)',
        })),
        ...(breakdown.othersTotal !== null
          ? [
              {
                label: t('chart.others', { count: breakdown.othersDonationCount }),
                value: breakdown.othersTotal,
                color: 'var(--color-indigo)',
              },
            ]
          : []),
      ]
    : [];

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={t('title')}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2 className="font-display font-bold text-text">{t('title')}</h2>
          <button ref={closeButtonRef} className="modal-close" onClick={onClose} aria-label={t('close')}>
            ×
          </button>
        </div>
        <div className="modal-body flex flex-col gap-4">
          {isLoading && (
            <p className="text-sm text-text-2" aria-live="polite">
              {t('loading')}
            </p>
          )}
          {error && (
            <p className="text-sm text-coral" role="alert">
              {t('error')}
            </p>
          )}
          {breakdown && (
            <>
              <p className="text-sm text-text-2">
                {breakdown.payoutLabel} —{' '}
                <span className="font-display font-bold text-text">{fmtEur(breakdown.payoutAmount)}</span>
              </p>

              <Donut slices={slices} emptyKey="donor.payoutBreakdown.chartEmpty" />

              <div>
                <h3 className="font-display font-bold text-sm mb-2">{t('myLines.title')}</h3>
                {breakdown.myLines.length === 0 ? (
                  <p className="text-sm text-text-2">{t('myLines.empty')}</p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {breakdown.myLines.map((line) => (
                      <li key={line.donationId} className="flex items-center justify-between text-sm gap-2">
                        <span>{fmtDate(line.confirmedAt)}</span>
                        <span className="font-display font-bold">{fmtEur(line.amount)}</span>
                        <button
                          type="button"
                          className="btn-icon"
                          onClick={() => onOpenTraceability(line.donationId)}
                          aria-label={t('myLines.viewTraceabilityAria', { date: fmtDate(line.confirmedAt) })}
                          title={t('myLines.viewTraceabilityAria', { date: fmtDate(line.confirmedAt) })}
                        >
                          <span aria-hidden="true">🔍</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {breakdown.othersTotal !== null ? (
                <p className="text-sm text-text-2">
                  {t('others.withAmount', {
                    count: breakdown.othersDonationCount,
                    amount: fmtEur(breakdown.othersTotal),
                  })}
                </p>
              ) : breakdown.othersDonationCount > 0 ? (
                <p className="text-sm text-text-2">{t('others.hidden')}</p>
              ) : null}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
