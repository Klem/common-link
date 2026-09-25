'use client';

import { useState, useMemo, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import type { UsePaymentsReturn } from '@/hooks/campaign/usePayments';
import { Donut, DONUT_PALETTE } from '@/components/ui/Donut';
import { PaymentJournal } from '@/components/campaign/PaymentJournal';
import { PayoutDetailPanel } from '@/components/campaign/PayoutDetailPanel';
import { IssuePaymentForm } from '@/components/campaign/IssuePaymentForm';
import { fmtDate, fmtEur } from '@/components/campaign/payoutDisplay';
import { useToastStore } from '@/stores/toastStore';
import { PayoutStatus, isPayoutInFlight, needsBankAuthorisation } from '@/types/payment';
import type { CampaignDto } from '@/types/campaign';
import type { PayoutDto } from '@/types/payment';

interface Props {
  campaign: CampaignDto;
  payments: UsePaymentsReturn;
}

/** Preset accounting codes. Each has a `typeCodes.*` label; anything else is free text. */
const PRESET_TYPE_CODES = new Set<string>([
  '60-mat', '60-svc', '61-loc', '61-ent', '62-tra', '62-pub', '64-rem', '64-soc', '65-ges',
]);

export function CampaignPaymentsTab({ campaign, payments }: Props) {
  const t = useTranslations('dashboard.campaigns.payments');
  const { payouts, summary, isLoading, isSaving, error, retry, awaitingReturnPayoutId } = payments;
  const addToast = useToastStore((s) => s.addToast);

  /** Expense line expanded in the breakdown. One at a time, so the list never runs long. */
  const [openBreakdownCode, setOpenBreakdownCode] = useState<string | null>(null);
  /** Payout whose detail panel is open, by id — not by object, so a poll refresh keeps it live. */
  const [detailPayoutId, setDetailPayoutId] = useState<string | null>(null);

  /** Human label of a payout type: the translated preset, or the free text of a custom code. */
  const typeLabel = useCallback(
    (code: string) => (PRESET_TYPE_CODES.has(code) ? t(`typeCodes.${code}`) : code),
    [t],
  );

  /**
   * The backend reports payouts as not issuable — production running Bridge in demo mode, where a
   * transfer would be simulated, never sent to a bank, yet shown as settled. Better an explicitly
   * disabled button than a payment that never happened. Local and staging stay enabled, so the
   * demo journey remains exercisable. Stays enabled while the summary loads, so no misleading
   * tooltip flashes on mount.
   */
  const paymentsDisabled = summary?.paymentsEnabled === false;

  /**
   * Issues an existing payout again — the previous attempt moved no money.
   *
   * Deliberately not `submit`: re-creating would duplicate the accounting row the association
   * already filled in. Only a fresh authorisation link is needed, and the backend mints one.
   */
  const handleRetry = useCallback(async (payoutId: string) => {
    try {
      const reissued = await retry(payoutId);
      if (needsBankAuthorisation(reissued) && reissued.bridgeCheckoutUrl) {
        addToast('success', 'paymentAwaitingBank');
        window.location.href = reissued.bridgeCheckoutUrl;
        return;
      }
      addToast('success', isPayoutInFlight(reissued) ? 'paymentSubmitted' : 'paymentSuccess');
    } catch {
      addToast('error', 'paymentError');
    }
  }, [retry, addToast]);

  /**
   * Settled payouts grouped by expense line, largest first. Feeds both the donut and the list below
   * it; the colour is fixed here so each list entry matches its slice.
   */
  const breakdown = useMemo(() => {
    const groups = new Map<string, PayoutDto[]>();
    payouts
      .filter((p) => p.status === PayoutStatus.CONFIRMED)
      .forEach((p) => groups.set(p.typeCode, [...(groups.get(p.typeCode) ?? []), p]));
    return [...groups.entries()]
      .map(([code, items]) => ({ code, items, total: items.reduce((s, p) => s + p.amount, 0) }))
      .sort((a, b) => b.total - a.total)
      .map((g, i) => ({ ...g, label: typeLabel(g.code), color: DONUT_PALETTE[i % DONUT_PALETTE.length] }));
  }, [payouts, typeLabel]);

  const donutSlices = useMemo(
    () => breakdown.map((g) => ({ label: g.label, value: g.total, color: g.color })),
    [breakdown],
  );

  const detailPayout = useMemo(
    () => payouts.find((p) => p.id === detailPayoutId) ?? null,
    [payouts, detailPayoutId],
  );

  return (
    <div>
      {/* ── Stats ─────────────────────────────────────────────────── */}
      <div className="cm-stats">
        <div className="cm-stat">
          <div className="cm-stat-icon">💰</div>
          <div className="cm-stat-lbl">{t('stats.availableBalance')}</div>
          <div className="cm-stat-val val-dark">
            {summary ? fmtEur(summary.availableBalance) : '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">📤</div>
          <div className="cm-stat-lbl">{t('stats.paid')}</div>
          <div className="cm-stat-val val-amber-color">
            {summary ? fmtEur(summary.confirmedAmount) : '—'}
          </div>
          {summary && (
            <div className="cm-stat-sub">
              {summary.confirmedCount} pmt{summary.confirmedCount !== 1 ? 's' : ''}
            </div>
          )}
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">⏳</div>
          <div className="cm-stat-lbl">{t('stats.pending')}</div>
          <div className="cm-stat-val val-teal">
            {summary ? fmtEur(summary.pendingAmount) : '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">⚡</div>
          <div className="cm-stat-lbl">{t('stats.transactions')}</div>
          <div className="cm-stat-val val-teal">
            {summary?.txTotal ?? '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">💚</div>
          <div className="cm-stat-lbl">{t('stats.confirmed')}</div>
          <div className="cm-stat-val val-dark">
            {summary?.txConfirmed ?? '—'}
          </div>
        </div>
      </div>

      {/* ── Two-column grid: issue on the left, what was spent on the right ─ */}
      <div className="pay-form-grid mb-18">
        <IssuePaymentForm
          campaignId={campaign.id}
          submit={payments.submit}
          isSaving={isSaving}
          paymentsDisabled={paymentsDisabled}
        />

        <div className="cm-card">
          <div className="cm-card-title">{t('breakdown.title')}</div>
          {/* Top half: the chart. Its legend is the list below, which also details each line. */}
          <div className="cm-donut-center">
            <Donut slices={donutSlices} emptyKey="campaigns.payments.breakdown.empty" legend={false} />
          </div>

          {/* Bottom half: one expandable entry per expense line, listing its transactions. */}
          {breakdown.length > 0 && (
            <div className="breakdown-list">
              {breakdown.map((g) => (
                <details
                  key={g.code}
                  className="breakdown-item"
                  open={openBreakdownCode === g.code}
                  onToggle={(e) => {
                    const { open } = e.currentTarget;
                    setOpenBreakdownCode((cur) => (open ? g.code : cur === g.code ? null : cur));
                  }}
                >
                  <summary className="breakdown-summary">
                    <span className="breakdown-dot" style={{ background: g.color }} />
                    <span className="breakdown-label" title={g.label}>{g.label}</span>
                    <span className="breakdown-count">{t('breakdown.count', { count: g.items.length })}</span>
                    <span className="breakdown-total">{fmtEur(g.total)}</span>
                    <span className="breakdown-chev" aria-hidden="true">▾</span>
                  </summary>
                  <div className="breakdown-body">
                    {g.items.map((p) => (
                      <div key={p.id} className="pay-row">
                        <div className="pay-row-main">
                          <div className="pay-row-name">{p.payeeName}</div>
                          <div className="cm-hint-sm">{fmtDate(p.createdAt)}</div>
                        </div>
                        <span className="pay-row-amount">{fmtEur(p.amount)}</span>
                      </div>
                    ))}
                  </div>
                </details>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ── Journal ───────────────────────────────────────────────── */}
      <PaymentJournal
        payouts={payouts}
        isLoading={isLoading}
        error={error}
        isSaving={isSaving}
        awaitingReturnPayoutId={awaitingReturnPayoutId}
        typeLabel={typeLabel}
        onRetry={handleRetry}
        onOpen={(p) => setDetailPayoutId(p.id)}
      />

      <PayoutDetailPanel
        payout={detailPayout}
        reference={detailPayout?.bridgePaymentTransactionId ?? ''}
        typeLabel={detailPayout ? typeLabel(detailPayout.typeCode) : ''}
        awaitingReturnPayoutId={awaitingReturnPayoutId}
        isSaving={isSaving}
        onRetry={handleRetry}
        onClose={() => setDetailPayoutId(null)}
      />
    </div>
  );
}
