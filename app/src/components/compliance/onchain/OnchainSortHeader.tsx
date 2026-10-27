'use client';

import { useTranslations } from 'next-intl';

/** Direction of a sorted column. */
export const SortDirection = {
  ASC: 'ASC',
  DESC: 'DESC',
} as const;
export type SortDirection = typeof SortDirection[keyof typeof SortDirection];

interface Props {
  label: string;
  active: boolean;
  direction: SortDirection;
  onSort: () => void;
}

/**
 * Sortable column header of the « Blockchain » compliance tables (`th-sort` pattern of
 * `components/donor/DonationHistoryTable.tsx`).
 */
export function OnchainSortHeader({ label, active, direction, onSort }: Props) {
  const t = useTranslations('compliance.onchain.table');
  const next = active && direction === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC;
  return (
    <button
      type="button"
      className={`th-sort${active ? ' active' : ''}`}
      onClick={onSort}
      aria-label={next === SortDirection.ASC ? t('sortAscendingAria', { column: label }) : t('sortDescendingAria', { column: label })}
    >
      {label}
      <span className={`th-sort-chev${active && direction === SortDirection.DESC ? ' desc' : ''}`} aria-hidden="true">
        ▲
      </span>
    </button>
  );
}

/**
 * Sort state of one table: clicking the active column flips its direction, another column starts ascending.
 *
 * @returns the next `[key, direction]` pair.
 */
export function nextSort<K>(current: K | null, direction: SortDirection, clicked: K): [K, SortDirection] {
  if (current === clicked) return [clicked, direction === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC];
  return [clicked, SortDirection.ASC];
}
