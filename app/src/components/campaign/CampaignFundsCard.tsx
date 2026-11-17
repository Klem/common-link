'use client';

import { useEffect, useMemo, useState } from 'react';
import { useFormatter, useTranslations } from 'next-intl';
import { InfoTooltip } from '@/components/ui/InfoTooltip';
import { fmtDate, fmtEur } from '@/components/campaign/payoutDisplay';
import { HELLOASSO_ADMIN_URL } from '@/components/dashboard/helloasso-onboarding/helloAssoFlow';
import { CashOutConfirmedBy, CashOutState, FundsProvider } from '@/types/campaignFunds';
import type { CampaignCashOut, CampaignFunds } from '@/types/campaignFunds';

/** The four steps of a donation, left to right; the colour class of each bar segment. */
const FundsStep = {
  AT_PROVIDER: 'atProvider',
  PAYOUT_PREPARING: 'payoutPreparing',
  IN_TRANSIT: 'inTransit',
  AVAILABLE: 'available',
} as const;
type FundsStep = typeof FundsStep[keyof typeof FundsStep];

const SEGMENT_CLASS: Record<FundsStep, string> = {
  atProvider: 'funds-seg-ha',
  payoutPreparing: 'funds-seg-prep',
  inTransit: 'funds-seg-transit',
  available: 'funds-seg-avail',
};

interface Props {
  funds: CampaignFunds | null;
  isLoading: boolean;
  /** The last read failed. */
  error: boolean;
  refreshing: boolean;
  onRefresh: () => void;
  /** Opens "Avez-vous reçu ce virement ?" for a payout in transit. */
  onOpenReceipt: (cashOut: CampaignCashOut) => void;
}

/**
 * Whether the block applies: hidden for a Mollie association, unless HelloAsso donations from before a change of
 * provider are still on their way (brief FD6-00 §7.1, E6) — their confirmation must stay reachable.
 */
export function showsFundsCard(funds: CampaignFunds): boolean {
  if (funds.provider !== FundsProvider.MOLLIE) return true;
  return funds.atProvider + funds.payoutPreparing + funds.inTransit > 0 || funds.cashOuts.length > 0;
}

/**
 * "Où en sont vos dons" (Payments tab): where the campaign's donations stand between HelloAsso and the association's
 * bank account, as a left-to-right timeline bar with a legend whose entries open a tooltip (detail, payouts to
 * confirm, link to HelloAsso), and the amount a payment can use. Every amount comes from the backend
 * (`CampaignFunds`); nothing is added up here except the bar's proportions.
 *
 * States: skeleton on first load; nothing for a Mollie association (see {@link showsFundsCard}); warning banner with
 * the last data when HelloAsso tracking failed; short warning when the funds could not be read at all.
 */
export function CampaignFundsCard({ funds, isLoading, error, refreshing, onRefresh, onOpenReceipt }: Props) {
  const t = useTranslations('dashboard.campaigns.payments.funds');
  const format = useFormatter();
  const [pinned, setPinned] = useState<FundsStep | null>(null);
  const [hoveredSegment, setHoveredSegment] = useState<FundsStep | null>(null);
  const [now, setNow] = useState(() => Date.now());

  // Keeps "Mis à jour il y a …" current, and re-enables "Actualiser" when the server allows a new pass.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);
  const nextAt = funds?.nextRefreshAvailableAt ? Date.parse(funds.nextRefreshAvailableAt) : null;
  useEffect(() => {
    if (nextAt === null || nextAt <= Date.now()) return;
    const timer = setTimeout(() => setNow(Date.now()), nextAt - Date.now() + 50);
    return () => clearTimeout(timer);
  }, [nextAt]);

  const steps = useMemo(() => {
    if (!funds) return [];
    const all: { key: FundsStep; amount: number }[] = [
      { key: FundsStep.AT_PROVIDER, amount: funds.atProvider },
      { key: FundsStep.PAYOUT_PREPARING, amount: funds.payoutPreparing },
      { key: FundsStep.IN_TRANSIT, amount: funds.inTransit },
      { key: FundsStep.AVAILABLE, amount: funds.available },
    ];
    return all.filter((s) => s.amount > 0 || s.key === FundsStep.AVAILABLE);
  }, [funds]);

  if (!funds && !isLoading && !error) return null;
  if (funds && !showsFundsCard(funds)) return null;

  const title = (
    <div className="cm-card-title">
      <span aria-hidden="true">💶</span> {t('title')}
      {funds && <SyncLine funds={funds} now={now} refreshing={refreshing} onRefresh={onRefresh} />}
    </div>
  );

  if (!funds) {
    return (
      <div className="cm-card" id="funds-card">
        {title}
        {isLoading ? (
          <div aria-busy="true">
            <div className="skel funds-skel" />
            <div className="skel funds-skel w70" />
            <div className="skel funds-skel total" />
          </div>
        ) : (
          <div className="alert alert-warn" role="status">
            <span className="alert-icon" aria-hidden="true">⚠️</span>
            <span>{t('loadError')} </span>
            <button type="button" className="cm-btn cm-btn-sm" onClick={onRefresh} disabled={refreshing}>{t('refresh')}</button>
          </div>
        )}
      </div>
    );
  }

  const total = steps.reduce((s, step) => s + step.amount, 0);
  const inTransitCashOuts = funds.cashOuts.filter((c) => c.state === CashOutState.IN_TRANSIT);

  return (
    <div className="cm-card" id="funds-card">
      {title}

      {funds.syncError && (
        <div className="alert alert-warn" role="status">
          <span className="alert-icon" aria-hidden="true">⚠️</span>
          <span>
            <strong>{t('syncError.title')}</strong>{' '}
            {funds.lastSyncAt
              ? t('syncError.since', { date: format.dateTime(new Date(funds.lastSyncAt), { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) })
              : t('syncError.noDate')}
          </span>
        </div>
      )}

      <div className="funds-bar" role="img" aria-label={t('barLabel')}>
        {steps.map((s) => (
          <span
            key={s.key}
            className={SEGMENT_CLASS[s.key]}
            style={{ width: `${total > 0 ? (s.amount / total) * 100 : 0}%` }}
            onMouseEnter={() => setHoveredSegment(s.key)}
            onMouseLeave={() => setHoveredSegment(null)}
            // The segment is outside its tooltip: keep the tooltip's "click outside" from unpinning it first.
            onMouseDown={(e) => e.nativeEvent.stopPropagation()}
            onClick={() => setPinned((cur) => (cur === s.key ? null : s.key))}
          />
        ))}
      </div>

      <div className="funds-legend">
        {steps.map((s, i) => (
          <InfoTooltip
            key={s.key}
            id={`funds-tip-${s.key}`}
            className="funds-leg-wrap"
            triggerClassName="funds-leg-btn"
            tipClassName={`funds-tip${steps.length > 2 && i === steps.length - 1 ? ' right' : ''}`}
            pinned={pinned === s.key}
            onPinnedChange={(p) => setPinned(p ? s.key : null)}
            highlighted={hoveredSegment === s.key}
            trigger={
              <span className="funds-leg">
                <i className={SEGMENT_CLASS[s.key]} aria-hidden="true" />
                <span>
                  <strong>{fmtEur(s.amount)}</strong>
                  {t(`steps.${s.key}.label`)}
                  <span className="funds-info" aria-hidden="true">ⓘ</span>
                  <br />
                  <span>{t(`steps.${s.key}.sub`)}</span>
                  {s.key === FundsStep.IN_TRANSIT && inTransitCashOuts.length > 0 && (
                    <>
                      <br />
                      <span className="funds-cue">{t('toConfirm', { count: inTransitCashOuts.length })}</span>
                    </>
                  )}
                </span>
              </span>
            }
          >
            <div className="funds-tip-in">
              <div className="funds-tip-title">{t(`steps.${s.key}.label`)}</div>
              <StepTip
                step={s.key}
                funds={funds}
                onOpenReceipt={(c) => {
                  // Keeps "En route" open behind the dialog, so closing it gives the focus back to this button.
                  setPinned(FundsStep.IN_TRANSIT);
                  onOpenReceipt(c);
                }}
              />
            </div>
          </InfoTooltip>
        ))}
      </div>

      <div className={`funds-total${funds.disbursable <= 0 ? ' zero' : ''}`}>
        <div className="funds-total-val">{fmtEur(funds.disbursable)}</div>
        <div className="funds-total-lbl">
          <strong>{t('total.label')}</strong>
          <br />
          {t('total.formula', { available: fmtEur(funds.available), paid: fmtEur(funds.paidOut), pending: fmtEur(funds.pendingPayouts) })}
        </div>
      </div>
      <p className="funds-foot">{t('foot')}</p>
    </div>
  );
}

/** "Mis à jour il y a … · Actualiser", disabled while a request runs and until the server allows a new pass. */
function SyncLine({ funds, now, refreshing, onRefresh }: { funds: CampaignFunds; now: number; refreshing: boolean; onRefresh: () => void }) {
  const t = useTranslations('dashboard.campaigns.payments.funds');
  const format = useFormatter();
  const next = funds.nextRefreshAvailableAt ? new Date(funds.nextRefreshAvailableAt) : null;
  const waiting = next !== null && next.getTime() > now;
  return (
    <span className="funds-sync" aria-live="polite">
      {refreshing ? (
        t('refreshing')
      ) : (
        <>
          {funds.lastSyncAt && t('updated', { relative: format.relativeTime(new Date(funds.lastSyncAt), now) })}
          {waiting ? (
            <>
              {funds.lastSyncAt && ' · '}
              {t('nextRefresh', { time: format.dateTime(next!, { hour: '2-digit', minute: '2-digit' }) })}
            </>
          ) : (
            <>
              {funds.lastSyncAt && ' ·'}
              <button type="button" onClick={onRefresh}>{t('refresh')}</button>
            </>
          )}
        </>
      )}
    </span>
  );
}

/** Content of a step's tooltip (brief FD6-00 §4). */
function StepTip({ step, funds, onOpenReceipt }: { step: FundsStep; funds: CampaignFunds; onOpenReceipt: (c: CampaignCashOut) => void }) {
  const t = useTranslations('dashboard.campaigns.payments.funds.tips');
  const ofTotal = (c: CampaignCashOut) =>
    c.netTotal != null && c.netTotal > c.campaignAmount ? t('inTransit.ofTotal', { total: fmtEur(c.netTotal) }) : '';
  const cashOutTitle = (c: CampaignCashOut) =>
    c.cashOutDate ? t('inTransit.cashOutTitle', { date: fmtDate(c.cashOutDate) }) : t('inTransit.cashOutTitleNoDate');

  switch (step) {
    case FundsStep.AT_PROVIDER:
      return (
        <>
          <div className="funds-tip-text">{t('atProvider.text')}</div>
          <a className="funds-tip-link" href={HELLOASSO_ADMIN_URL} target="_blank" rel="noopener noreferrer">
            {t('atProvider.link')}
          </a>
        </>
      );
    case FundsStep.PAYOUT_PREPARING:
      return <div className="funds-tip-text">{t('payoutPreparing.text')}</div>;
    case FundsStep.IN_TRANSIT:
      return (
        <>
          <div className="funds-tip-text">{t('inTransit.text')}</div>
          {funds.cashOuts.filter((c) => c.state === CashOutState.IN_TRANSIT).map((c) => (
            <div key={c.idCashOut} className="funds-tip-co">
              <div className="funds-tip-co-head"><span>{cashOutTitle(c)}</span><span>{fmtEur(c.campaignAmount)}</span></div>
              <div className="funds-tip-co-sub">
                {ofTotal(c)}
                {c.autoAvailableOn && t('inTransit.autoOn', { date: fmtDate(c.autoAvailableOn) })}
              </div>
              <button type="button" className="cm-btn cm-btn-primary cm-btn-sm" onClick={() => onOpenReceipt(c)}>
                {t('inTransit.confirm')}
              </button>
            </div>
          ))}
        </>
      );
    case FundsStep.AVAILABLE: {
      const lines = [
        ...funds.cashOuts.filter((c) => c.state === CashOutState.AVAILABLE).map((c) => ({
          key: `co-${c.idCashOut}`,
          at: c.cashOutDate ?? c.confirmedAt ?? '',
          head: cashOutTitle(c),
          amount: c.campaignAmount,
          sub: ofTotal(c) + confirmedText(c),
        })),
        ...funds.manualReleases.map((r) => ({
          key: `rel-${r.releasedAt}`,
          at: r.releasedAt,
          head: t('available.releaseTitle', { date: fmtDate(r.releasedAt) }),
          amount: r.campaignAmount,
          sub: t('available.releaseSub'),
        })),
      ].sort((a, b) => b.at.localeCompare(a.at));
      return (
        <>
          <div className="funds-tip-text">{t('available.text')}</div>
          {lines.map((l) => (
            <div key={l.key} className="funds-tip-co">
              <div className="funds-tip-co-head"><span>{l.head}</span><span>{fmtEur(l.amount)}</span></div>
              <div className="funds-tip-co-sub">{l.sub}</div>
            </div>
          ))}
          {lines.length === 0 && funds.available === 0 && (
            <div className="funds-tip-co"><div className="funds-tip-co-sub">{t('available.none')}</div></div>
          )}
        </>
      );
    }
  }

  function confirmedText(c: CampaignCashOut): string {
    const date = c.confirmedAt ?? c.autoAvailableOn;
    if (!date) return '';
    switch (c.confirmedBy) {
      case CashOutConfirmedBy.MANUAL: return t('available.confirmedManual', { date: fmtDate(date) });
      case CashOutConfirmedBy.CURATOR_RELEASE: return t('available.confirmedCurator', { date: fmtDate(date) });
      default: return t('available.confirmedAuto', { date: fmtDate(date) });
    }
  }
}
