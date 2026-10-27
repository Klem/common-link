'use client';

import { useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import type { OnchainControlEntryDto } from '@/types/compliance';
import { OnchainRegistryControlEvent } from '@/types/compliance';
import { OnchainSortHeader, SortDirection, nextSort } from './OnchainSortHeader';
import { formatOnchainDateTime } from './format';

const SortKey = {
  DATE: 'DATE',
  EVENT: 'EVENT',
  OFFICER: 'OFFICER',
} as const;
type SortKey = typeof SortKey[keyof typeof SortKey];

const EVENT_BADGE: Record<string, string> = {
  [OnchainRegistryControlEvent.PAUSED]: 'badge-warning',
  [OnchainRegistryControlEvent.UNPAUSED]: 'badge-success',
  [OnchainRegistryControlEvent.PAUSE_FAILED]: 'badge-neutral',
  [OnchainRegistryControlEvent.UNPAUSE_FAILED]: 'badge-neutral',
};

/** Reads a string field of a journal payload (shapes differ per event type), or null. */
function payloadString(payload: Record<string, unknown>, key: string): string | null {
  const value = payload?.[key];
  return typeof value === 'string' ? value : null;
}

/**
 * History of the freezes and resumes requested from the platform (compliance audit log): date, event, officer,
 * rationale, and the transaction, the no-op flag or the error type.
 */
export function OnchainControlHistoryTable({ entries }: { entries: OnchainControlEntryDto[] }) {
  const t = useTranslations('compliance.onchain.history');
  const locale = useLocale();
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [direction, setDirection] = useState<SortDirection>(SortDirection.ASC);

  const rows = useMemo(() => {
    if (!sortKey) return entries;
    const sorted = [...entries].sort((a, b) => {
      switch (sortKey) {
        case SortKey.DATE:
          return a.sequenceNo - b.sequenceNo;
        case SortKey.EVENT:
          return a.eventType.localeCompare(b.eventType);
        case SortKey.OFFICER:
          return (a.actorEmail ?? '').localeCompare(b.actorEmail ?? '');
      }
    });
    if (direction === SortDirection.DESC) sorted.reverse();
    return sorted;
  }, [entries, sortKey, direction]);

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

  if (entries.length === 0) return <p className="cm-table-empty">{t('empty')}</p>;

  return (
    <div className="overflow-x-auto tw">
      <table className="cm-table" aria-label={t('title')}>
        <caption className="sr-only">{t('title')}</caption>
        <thead>
          <tr>
            <th scope="col">{header(SortKey.DATE, t('col.date'))}</th>
            <th scope="col">{header(SortKey.EVENT, t('col.event'))}</th>
            <th scope="col">{header(SortKey.OFFICER, t('col.officer'))}</th>
            <th scope="col">{t('col.rationale')}</th>
            <th scope="col">{t('col.detail')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((entry) => {
            const txHash = payloadString(entry.payload, 'txHash');
            const errorType = payloadString(entry.payload, 'errorType');
            return (
              <tr key={entry.sequenceNo}>
                <td>{formatOnchainDateTime(entry.occurredAt, locale)}</td>
                <td>
                  <span className={`badge ${EVENT_BADGE[entry.eventType] ?? 'badge-neutral'}`}>
                    {t(`event.${entry.eventType}`)}
                  </span>
                </td>
                <td className="break-all">{entry.actorEmail ?? t('unknownOfficer')}</td>
                <td className="break-words max-w-sm">{payloadString(entry.payload, 'rationale') ?? '—'}</td>
                <td className="text-xs">
                  {entry.payload?.alreadyPaused === true && t('alreadyPaused')}
                  {entry.payload?.alreadyActive === true && t('alreadyActive')}
                  {txHash && <span className="font-mono break-all">{txHash}</span>}
                  {errorType && t('error', { type: errorType })}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
