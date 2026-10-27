'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import {
  getOnchainJobFeed,
  getOnchainOutboxHealth,
  getOnchainStatus,
  listOnchainControlHistory,
  listOnchainFailedJobs,
  pauseOnchainRegistry,
  unpauseOnchainRegistry,
} from '@/lib/api/compliance';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { OnchainControlHistoryTable } from '@/components/compliance/onchain/OnchainControlHistoryTable';
import { OnchainFailedJobsTable } from '@/components/compliance/onchain/OnchainFailedJobsTable';
import { OnchainJobFeedTable } from '@/components/compliance/onchain/OnchainJobFeedTable';
import { formatOnchainDateTime, problemDetail } from '@/components/compliance/onchain/format';
import type {
  OnchainControlEntryDto,
  OnchainFailedJobDto,
  OnchainJobFeedDto,
  OnchainOutboxHealthDto,
  OnchainRegistryStatusDto,
} from '@/types/compliance';
import { ONCHAIN_RATIONALE_MAX_LENGTH } from '@/types/compliance';

/** Refresh period of the live panels (state, counters, feed). */
const POLL_INTERVAL_MS = 5_000;

/** Which registry action the officer is about to confirm. */
const RegistryAction = {
  PAUSE: 'PAUSE',
  UNPAUSE: 'UNPAUSE',
} as const;
type RegistryAction = typeof RegistryAction[keyof typeof RegistryAction];

/** Outcome of the last confirmed action, shown under the form. */
interface ActionResult {
  action: RegistryAction;
  /** True when nothing was sent: already paused (pause) or not paused (resume). */
  noop: boolean;
  txHash: string | null;
  blockNumber: number | null;
  explorerTxUrl: string | null;
}

/**
 * Compliance « Blockchain » section: state of the on-chain registry, emergency freeze and resume (both with a
 * mandatory rationale and a confirmation, journaled by the backend), outbox health, failed writes (read-only) and the
 * history of freezes and resumes.
 */
export default function ComplianceOnchainPage() {
  const locale = useLocale();
  const t = useTranslations('compliance.onchain');

  const [status, setStatus] = useState<OnchainRegistryStatusDto | null>(null);
  const [statusError, setStatusError] = useState(false);
  const [outbox, setOutbox] = useState<OnchainOutboxHealthDto | null>(null);
  const [failedJobs, setFailedJobs] = useState<OnchainFailedJobDto[] | null>(null);
  const [history, setHistory] = useState<OnchainControlEntryDto[] | null>(null);
  const [feed, setFeed] = useState<OnchainJobFeedDto | null>(null);
  const [sectionError, setSectionError] = useState(false);
  const [refreshedAt, setRefreshedAt] = useState<string | null>(null);
  /** True while a refresh runs: a slow API never gets two overlapping polls. */
  const inFlight = useRef(false);
  /** A history reload asked while a refresh was running: done by the next refresh. */
  const historyDue = useRef(false);

  const [rationale, setRationale] = useState('');
  const [pendingAction, setPendingAction] = useState<RegistryAction | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [result, setResult] = useState<ActionResult | null>(null);

  /**
   * Loads the live panels, one request at a time (the Axios rule of this app: never parallel): state, then — in real
   * mode only — the counters and failed writes (in simulated mode they are not on-chain writes), then the feed (shown
   * in both modes, flagged simulated). The history is reloaded only with [withHistory] (it changes with an action).
   */
  const load = useCallback(async (withHistory: boolean) => {
    if (inFlight.current) {
      if (withHistory) historyDue.current = true;
      return;
    }
    inFlight.current = true;
    const wantHistory = withHistory || historyDue.current;
    let real = false;
    try {
      const s = await getOnchainStatus();
      setStatus(s);
      real = !s.mock;
      setStatusError(false);
    } catch {
      setStatusError(true);
    }
    try {
      if (real) {
        setOutbox(await getOnchainOutboxHealth());
        setFailedJobs(await listOnchainFailedJobs());
      } else {
        setOutbox(null);
        setFailedJobs(null);
      }
      setFeed(await getOnchainJobFeed());
      if (wantHistory) {
        setHistory(await listOnchainControlHistory());
        historyDue.current = false;
      }
      setSectionError(false);
      setRefreshedAt(new Date().toISOString());
    } catch {
      setSectionError(true);
    } finally {
      inFlight.current = false;
    }
  }, []);

  useEffect(() => {
    void load(true);
    // Live refresh, suspended while the tab is hidden.
    const timer = window.setInterval(() => {
      if (!document.hidden) void load(false);
    }, POLL_INTERVAL_MS);
    const onVisible = () => {
      if (!document.hidden) void load(false);
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [load]);

  const paused = status?.paused ?? null;
  const action: RegistryAction = paused ? RegistryAction.UNPAUSE : RegistryAction.PAUSE;
  const isPause = action === RegistryAction.PAUSE;
  const trimmed = rationale.trim();

  /** Why the action is greyed out, or null when it can be used. */
  const disabledReason: string | null = !status
    ? null
    : status.mock
      ? t('disabled.mock')
      : paused === null
        ? t('disabled.unreadable')
        : paused && !status.canUnpause
          ? t('disabled.unpauseRevoked')
          : null;

  const handleConfirm = async () => {
    if (!pendingAction) return;
    const confirmed = pendingAction;
    setPendingAction(null);
    setIsSubmitting(true);
    setActionError(null);
    setResult(null);
    try {
      if (confirmed === RegistryAction.PAUSE) {
        const r = await pauseOnchainRegistry(trimmed);
        setResult({ action: confirmed, noop: r.alreadyPaused, txHash: r.txHash, blockNumber: r.blockNumber, explorerTxUrl: r.explorerTxUrl });
      } else {
        const r = await unpauseOnchainRegistry(trimmed);
        setResult({ action: confirmed, noop: r.alreadyActive, txHash: r.txHash, blockNumber: r.blockNumber, explorerTxUrl: r.explorerTxUrl });
      }
      setRationale('');
    } catch (err: unknown) {
      setActionError(problemDetail(err) ?? t('action.error'));
    } finally {
      setIsSubmitting(false);
      await load(true);
    }
  };

  const stateBadge = !status
    ? null
    : status.mock
      ? { cls: 'badge-neutral', label: t('state.mock') }
      : paused === null
        ? { cls: 'badge-neutral', label: t('state.unreadable') }
        : paused
          ? { cls: 'badge-warning', label: t('state.paused') }
          : { cls: 'badge-success', label: t('state.active') };

  const resultMessage = !result
    ? null
    : result.action === RegistryAction.PAUSE
      ? (result.noop ? t('result.alreadyPaused') : t('result.paused'))
      : (result.noop ? t('result.alreadyActive') : t('result.unpaused'));

  return (
    <div className="main-content">
      <h1 className="cm-card-title">{t('title')}</h1>
      <p className="text-text-2 mb-4">{t('intro')}</p>

      {/* Registry state */}
      <div className="cm-card">
        <div className="cm-card-title">{t('state.title')}</div>
        {statusError ? (
          <p className="alert-warning">{t('loadError')}</p>
        ) : !status ? (
          <p>{t('loading')}</p>
        ) : (
          <dl className="grid gap-2">
            <div>
              <dt className="cm-label inline">{t('state.label')}</dt>{' '}
              <dd className="inline">{stateBadge && <span className={`badge ${stateBadge.cls}`}>{stateBadge.label}</span>}</dd>
            </div>
            {!status.mock && (
              <>
                <div>
                  <dt className="cm-label inline">{t('state.chain')}</dt> <dd className="inline">{status.chainId}</dd>
                </div>
                <div>
                  <dt className="cm-label inline">{t('state.address')}</dt>{' '}
                  <dd className="inline font-mono break-all">
                    {status.explorerAddressUrl ? (
                      <a href={status.explorerAddressUrl} target="_blank" rel="noopener noreferrer">
                        {status.registryAddress}
                      </a>
                    ) : (
                      status.registryAddress
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="cm-label inline">{t('state.resumeRight')}</dt>{' '}
                  <dd className="inline">{status.canUnpause ? t('state.resumeRightYes') : t('state.resumeRightNo')}</dd>
                </div>
              </>
            )}
          </dl>
        )}
      </div>

      {/* Freeze / resume */}
      {status && (
        <div className="cm-card">
          <div className="cm-card-title">{isPause ? t('action.pauseTitle') : t('action.unpauseTitle')}</div>
          <p className="text-text-2 mb-3">{isPause ? t('action.pauseDescription') : t('action.unpauseDescription')}</p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!disabledReason && trimmed) setPendingAction(action);
            }}
          >
            <div className="fg">
              <label className="cm-label" htmlFor="onchainRationale">
                {t('action.rationaleLabel')}
              </label>
              <textarea
                id="onchainRationale"
                className="fi"
                rows={3}
                maxLength={ONCHAIN_RATIONALE_MAX_LENGTH}
                value={rationale}
                onChange={(e) => setRationale(e.target.value)}
                placeholder={t('action.rationalePlaceholder')}
                disabled={!!disabledReason || isSubmitting}
                required
              />
              <p className="text-xs text-text-2 text-right">
                {t('action.counter', { count: rationale.length, max: ONCHAIN_RATIONALE_MAX_LENGTH })}
              </p>
            </div>
            {disabledReason && <p className="text-text-2 mb-3">{disabledReason}</p>}
            {actionError && <p className="text-error mb-3" role="alert">{actionError}</p>}
            <button
              type="submit"
              className={isPause ? 'btn btn-coral' : 'btn btn-secondary'}
              disabled={!!disabledReason || isSubmitting || !trimmed}
            >
              {isSubmitting ? t('action.submitting') : isPause ? t('action.pauseButton') : t('action.unpauseButton')}
            </button>
          </form>

          {result && (
            <div className="mt-4" aria-live="polite">
              <p className="text-success">{resultMessage}</p>
              {result.txHash && (
                <p className="text-sm">
                  <span className="cm-label">{t('result.tx')}</span>{' '}
                  {result.explorerTxUrl ? (
                    <a href={result.explorerTxUrl} target="_blank" rel="noopener noreferrer" className="font-mono break-all">
                      {result.txHash}
                    </a>
                  ) : (
                    <span className="font-mono break-all">{result.txHash}</span>
                  )}
                  {result.blockNumber !== null && <> — {t('result.block', { block: result.blockNumber })}</>}
                </p>
              )}
            </div>
          )}
        </div>
      )}

      {sectionError && <p className="alert-warning">{t('loadError')}</p>}

      {/* Live feed of the writes (every mode) */}
      {feed && (
        <div className="cm-card">
          <div className="cm-card-title">{t('feed.title')}</div>
          <p className="text-text-2 mb-3">{feed.simulated ? t('feed.descriptionSimulated') : t('feed.description')}</p>
          <p className="text-xs text-text-2 mb-3" aria-live="polite">
            {t('feed.refreshed', { date: formatOnchainDateTime(refreshedAt, locale), seconds: POLL_INTERVAL_MS / 1000 })}
          </p>
          <OnchainJobFeedTable items={feed.items} simulated={feed.simulated} />
        </div>
      )}

      {/* Outbox health */}
      {outbox && (
        <div className="cm-card">
          <div className="cm-card-title">{t('outbox.title')}</div>
          <p className="text-text-2 mb-3">{t('outbox.description')}</p>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div>
              <div className="cm-label">{t('outbox.pending')}</div>
              <div className="stat-card__value">{outbox.pending}</div>
            </div>
            <div>
              <div className="cm-label">{t('outbox.running')}</div>
              <div className="stat-card__value">{outbox.running}</div>
            </div>
            <div>
              <div className="cm-label">{t('outbox.failed')}</div>
              <div className="stat-card__value">{outbox.failed}</div>
            </div>
            <div>
              <div className="cm-label">{t('outbox.blocked')}</div>
              <div className="stat-card__value">{outbox.blockedByFailedDependency}</div>
            </div>
          </div>
          <p className="text-sm text-text-2 mt-3">
            {t('outbox.oldestPending', { date: formatOnchainDateTime(outbox.oldestPendingSince, locale) })}
          </p>
        </div>
      )}

      {/* Failed jobs (read-only) */}
      {failedJobs && (
        <div className="cm-card">
          <div className="cm-card-title">{t('failed.title')}</div>
          <p className="text-text-2 mb-3">{t('failed.description')}</p>
          <OnchainFailedJobsTable jobs={failedJobs} />
        </div>
      )}

      {/* Freeze / resume history */}
      {history && (
        <div className="cm-card">
          <div className="cm-card-title">{t('history.title')}</div>
          <OnchainControlHistoryTable entries={history} />
        </div>
      )}

      <ConfirmDialog
        isOpen={pendingAction !== null}
        title={pendingAction === RegistryAction.UNPAUSE ? t('confirm.unpauseTitle') : t('confirm.pauseTitle')}
        message={pendingAction === RegistryAction.UNPAUSE ? t('confirm.unpauseMessage') : t('confirm.pauseMessage')}
        confirmLabel={pendingAction === RegistryAction.UNPAUSE ? t('action.unpauseButton') : t('action.pauseButton')}
        variant={pendingAction === RegistryAction.UNPAUSE ? 'default' : 'danger'}
        onConfirm={handleConfirm}
        onCancel={() => setPendingAction(null)}
      />
    </div>
  );
}
