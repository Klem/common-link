'use client';

import { useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import type { OnchainFailedJobDto } from '@/types/compliance';
import { OnchainSortHeader, SortDirection, nextSort } from './OnchainSortHeader';
import { formatOnchainDateTime } from './format';

const SortKey = {
  ACTION: 'ACTION',
  ATTEMPTS: 'ATTEMPTS',
  FAILED_AT: 'FAILED_AT',
} as const;
type SortKey = typeof SortKey[keyof typeof SortKey];

function compare(a: OnchainFailedJobDto, b: OnchainFailedJobDto, key: SortKey): number {
  switch (key) {
    case SortKey.ACTION:
      return a.action.localeCompare(b.action);
    case SortKey.ATTEMPTS:
      return a.attempts - b.attempts;
    case SortKey.FAILED_AT:
      return a.updatedAt.localeCompare(b.updatedAt);
  }
}

/**
 * Failed on-chain registry writes, read-only: resetting a job is an operator decision, never a compliance one.
 * Errors arrive with their URLs already masked by the backend.
 */
export function OnchainFailedJobsTable({ jobs }: { jobs: OnchainFailedJobDto[] }) {
  const t = useTranslations('compliance.onchain.failed');
  const locale = useLocale();
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [direction, setDirection] = useState<SortDirection>(SortDirection.ASC);

  const rows = useMemo(() => {
    if (!sortKey) return jobs;
    const sorted = [...jobs].sort((a, b) => compare(a, b, sortKey));
    if (direction === SortDirection.DESC) sorted.reverse();
    return sorted;
  }, [jobs, sortKey, direction]);

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

  if (jobs.length === 0) return <p className="cm-table-empty">{t('empty')}</p>;

  return (
    <div className="overflow-x-auto tw">
      <table className="cm-table" aria-label={t('title')}>
        <caption className="sr-only">{t('title')}</caption>
        <thead>
          <tr>
            <th scope="col">{header(SortKey.ACTION, t('col.action'))}</th>
            <th scope="col">{header(SortKey.ATTEMPTS, t('col.attempts'))}</th>
            <th scope="col">{t('col.error')}</th>
            <th scope="col">{header(SortKey.FAILED_AT, t('col.failedAt'))}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((job) => (
            <tr key={job.id}>
              <td className="font-mono text-sm">{job.action}</td>
              <td>{job.attempts}</td>
              <td className="text-xs break-words max-w-md">{job.error ?? '—'}</td>
              <td>{formatOnchainDateTime(job.updatedAt, locale)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
