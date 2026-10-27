'use client';

import { useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import type { OnchainJobFeedItemDto } from '@/types/compliance';
import { OnchainJobStatus } from '@/types/compliance';
import { OnchainSortHeader, SortDirection, nextSort } from './OnchainSortHeader';
import { formatOnchainDateTime } from './format';

const SortKey = {
  QUEUED_AT: 'QUEUED_AT',
  ACTION: 'ACTION',
  STATUS: 'STATUS',
} as const;
type SortKey = typeof SortKey[keyof typeof SortKey];

const STATUS_BADGE: Record<string, string> = {
  [OnchainJobStatus.PENDING]: 'badge-warning',
  [OnchainJobStatus.RUNNING]: 'badge-info',
  [OnchainJobStatus.DONE]: 'badge-success',
  [OnchainJobStatus.FAILED]: 'badge-neutral',
};

function compare(a: OnchainJobFeedItemDto, b: OnchainJobFeedItemDto, key: SortKey): number {
  switch (key) {
    case SortKey.QUEUED_AT:
      return a.createdAt.localeCompare(b.createdAt);
    case SortKey.ACTION:
      return a.action.localeCompare(b.action);
    case SortKey.STATUS:
      return a.status.localeCompare(b.status);
  }
}

interface Props {
  items: OnchainJobFeedItemDto[];
  /** Simulated mode: hashes are random and exist on no chain. */
  simulated: boolean;
}

/**
 * Live feed of the registry writes queued by the platform (any status), newest first by default. Read-only; the
 * parent page refreshes it periodically.
 */
export function OnchainJobFeedTable({ items, simulated }: Props) {
  const t = useTranslations('compliance.onchain.feed');
  const locale = useLocale();
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [direction, setDirection] = useState<SortDirection>(SortDirection.ASC);

  const rows = useMemo(() => {
    if (!sortKey) return items;
    const sorted = [...items].sort((a, b) => compare(a, b, sortKey));
    if (direction === SortDirection.DESC) sorted.reverse();
    return sorted;
  }, [items, sortKey, direction]);

  const header = (key: SortKey, label: string) => (
    <OnchainSortHeader
      label={label}
      active={sortKey === key}
      direction={direction}
      onSort={() => {
        const [k, d] = nextSort(sortKey, direction, key);
        setSortKey(k);
        setDirection(d);
      }}
    />
  );

  if (items.length === 0) return <p className="cm-table-empty">{t('empty')}</p>;

  return (
    <div className="overflow-x-auto tw">
      <table className="cm-table" aria-label={t('title')}>
        <caption className="sr-only">{t('title')}</caption>
        <thead>
          <tr>
            <th scope="col">{header(SortKey.QUEUED_AT, t('col.queuedAt'))}</th>
            <th scope="col">{header(SortKey.ACTION, t('col.action'))}</th>
            <th scope="col">{header(SortKey.STATUS, t('col.status'))}</th>
            <th scope="col">{t('col.transaction')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((job) => (
            <tr key={job.id}>
              <td>{formatOnchainDateTime(job.createdAt, locale)}</td>
              <td className="font-mono text-sm">{job.action}</td>
              <td>
                <span className={`badge ${STATUS_BADGE[job.status] ?? 'badge-neutral'}`}>{t(`status.${job.status}`)}</span>
                {job.attempts > 1 && <span className="text-xs text-text-2"> {t('attempts', { count: job.attempts })}</span>}
              </td>
              <td className="text-xs">
                {job.txHash ? (
                  <>
                    {job.explorerTxUrl ? (
                      <a href={job.explorerTxUrl} target="_blank" rel="noopener noreferrer" className="font-mono break-all">
                        {job.txHash}
                      </a>
                    ) : (
                      <span className="font-mono break-all">{job.txHash}</span>
                    )}
                    {job.blockNumber !== null && <> — {t('block', { block: job.blockNumber })}</>}
                    {simulated && <> <span className="badge badge-neutral">{t('simulatedBadge')}</span></>}
                  </>
                ) : job.error ? (
                  <span className="break-words">{job.error}</span>
                ) : (
                  '—'
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
