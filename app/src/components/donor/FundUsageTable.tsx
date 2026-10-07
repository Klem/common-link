'use client';

import { Fragment, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Donut, DONUT_PALETTE } from '@/components/ui/Donut';
import type { SectionVariance } from '@/types/reporting';
import type { CampaignPayoutLineDto } from '@/types/donor';

interface Props {
  sections: SectionVariance[];
  payouts: CampaignPayoutLineDto[];
  onViewBreakdown: (payoutId: string) => void;
}

const SortKey = {
  CATEGORY: 'CATEGORY',
  PLANNED: 'PLANNED',
  SPENT: 'SPENT',
  REMAINING: 'REMAINING',
} as const;
type SortKey = typeof SortKey[keyof typeof SortKey];

const SortDirection = {
  ASC: 'asc',
  DESC: 'desc',
} as const;
type SortDirection = typeof SortDirection[keyof typeof SortDirection];

function fmtEur(amount: number): string {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  );
}

function remainingOf(section: SectionVariance): number {
  return section.planned - section.actual;
}

function compareSections(a: SectionVariance, b: SectionVariance, key: SortKey): number {
  switch (key) {
    case SortKey.CATEGORY:
      return a.sectionName.localeCompare(b.sectionName);
    case SortKey.PLANNED:
      return a.planned - b.planned;
    case SortKey.SPENT:
      return a.actual - b.actual;
    case SortKey.REMAINING:
      return remainingOf(a) - remainingOf(b);
  }
}

const DetailSortKey = {
  DATE: 'DATE',
  EXPENSE: 'EXPENSE',
  AMOUNT: 'AMOUNT',
} as const;
type DetailSortKey = typeof DetailSortKey[keyof typeof DetailSortKey];

function compareDetailPayouts(a: CampaignPayoutLineDto, b: CampaignPayoutLineDto, key: DetailSortKey): number {
  switch (key) {
    case DetailSortKey.DATE:
      return a.confirmedAt.localeCompare(b.confirmedAt);
    case DetailSortKey.EXPENSE:
      return a.label.localeCompare(b.label);
    case DetailSortKey.AMOUNT:
      return a.amount - b.amount;
  }
}

/**
 * "Utilisation des fonds" table — the donor-facing planned/spent/remaining breakdown per budget
 * section. Standard sortable `cm-table` (see app/CLAUDE.md "Tables"). Each row expands via its
 * chevron into a 2-column detail: the section's confirmed payouts (left) and a spent/remaining
 * donut (right). Only one section is expanded at a time.
 */
export function FundUsageTable({ sections, payouts, onViewBreakdown }: Props) {
  const t = useTranslations('dashboard.donor.report');
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [sortDirection, setSortDirection] = useState<SortDirection>(SortDirection.ASC);
  const [openSectionCode, setOpenSectionCode] = useState<string | null>(null);
  const [detailSortKey, setDetailSortKey] = useState<DetailSortKey | null>(null);
  const [detailSortDirection, setDetailSortDirection] = useState<SortDirection>(SortDirection.ASC);

  const sortedSections = useMemo(() => {
    if (!sortKey) return sections;
    const sorted = [...sections].sort((a, b) => compareSections(a, b, sortKey));
    if (sortDirection === SortDirection.DESC) sorted.reverse();
    return sorted;
  }, [sections, sortKey, sortDirection]);

  function handleSort(key: SortKey): void {
    if (sortKey === key) {
      setSortDirection((d) => (d === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC));
    } else {
      setSortKey(key);
      setSortDirection(SortDirection.ASC);
    }
  }

  function handleDetailSort(key: DetailSortKey): void {
    if (detailSortKey === key) {
      setDetailSortDirection((d) => (d === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC));
    } else {
      setDetailSortKey(key);
      setDetailSortDirection(SortDirection.ASC);
    }
  }

  function sortButton(label: string, active: boolean, direction: SortDirection, onClick: () => void) {
    const nextDirection = active && direction === SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC;
    const aria = nextDirection === SortDirection.ASC
      ? t('funds.sortAscendingAria', { column: label })
      : t('funds.sortDescendingAria', { column: label });
    return (
      <button type="button" className={`th-sort${active ? ' active' : ''}`} onClick={onClick} aria-label={aria}>
        {label}
        <span className={`th-sort-chev${active && direction === SortDirection.DESC ? ' desc' : ''}`} aria-hidden="true">
          ▲
        </span>
      </button>
    );
  }

  if (sections.length === 0) {
    return <p className="cm-table-empty">{t('funds.empty')}</p>;
  }

  return (
    <div className="overflow-x-auto tw">
      <table className="cm-table" aria-label={t('funds.title')}>
        <caption className="sr-only">{t('funds.title')}</caption>
        <thead>
          <tr>
            <th scope="col" />
            <th scope="col">{sortButton(t('funds.category'), sortKey === SortKey.CATEGORY, sortDirection, () => handleSort(SortKey.CATEGORY))}</th>
            <th scope="col">{sortButton(t('funds.planned'), sortKey === SortKey.PLANNED, sortDirection, () => handleSort(SortKey.PLANNED))}</th>
            <th scope="col">{sortButton(t('funds.spent'), sortKey === SortKey.SPENT, sortDirection, () => handleSort(SortKey.SPENT))}</th>
            <th scope="col">{sortButton(t('funds.remaining'), sortKey === SortKey.REMAINING, sortDirection, () => handleSort(SortKey.REMAINING))}</th>
          </tr>
        </thead>
        <tbody>
          {sortedSections.map((section) => {
            const isOpen = openSectionCode === section.sectionCode;
            const remaining = remainingOf(section);
            const sectionPayouts = payouts.filter((payout) => payout.sectionCode === section.sectionCode);
            const sortedSectionPayouts = detailSortKey
              ? [...sectionPayouts].sort((a, b) => {
                  const cmp = compareDetailPayouts(a, b, detailSortKey);
                  return detailSortDirection === SortDirection.DESC ? -cmp : cmp;
                })
              : sectionPayouts;
            const detailId = `funds-detail-${section.sectionCode}`;

            return (
              <Fragment key={section.sectionCode}>
                <tr>
                  <td>
                    <button
                      type="button"
                      className="btn-icon"
                      aria-expanded={isOpen}
                      aria-controls={detailId}
                      aria-label={`${isOpen ? t('funds.hideDetail') : t('funds.showDetail')} — ${section.sectionName}`}
                      onClick={() => setOpenSectionCode(isOpen ? null : section.sectionCode)}
                    >
                      <span className={`th-sort-chev${isOpen ? ' desc' : ''}`} aria-hidden="true">▲</span>
                    </button>
                  </td>
                  <td>{section.sectionName}</td>
                  <td className="amount-teal">{fmtEur(section.planned)}</td>
                  <td className="amount-coral">{fmtEur(section.actual)}</td>
                  <td className="amount-amber">{fmtEur(remaining)}</td>
                </tr>
                {isOpen && (
                  <tr id={detailId}>
                    <td colSpan={5}>
                      <div className="funds-detail-split">
                        <div className="overflow-x-auto">
                          {sectionPayouts.length === 0 ? (
                            <p className="cm-table-empty">{t('funds.detailEmpty')}</p>
                          ) : (
                            <table className="cm-table">
                              <thead>
                                <tr>
                                  <th scope="col">{sortButton(t('funds.detailDate'), detailSortKey === DetailSortKey.DATE, detailSortDirection, () => handleDetailSort(DetailSortKey.DATE))}</th>
                                  <th scope="col">{sortButton(t('funds.detailExpense'), detailSortKey === DetailSortKey.EXPENSE, detailSortDirection, () => handleDetailSort(DetailSortKey.EXPENSE))}</th>
                                  <th scope="col" className="col-right">{sortButton(t('funds.detailAmount'), detailSortKey === DetailSortKey.AMOUNT, detailSortDirection, () => handleDetailSort(DetailSortKey.AMOUNT))}</th>
                                  <th scope="col" />
                                </tr>
                              </thead>
                              <tbody>
                                {sortedSectionPayouts.map((payout) => (
                                  <tr key={payout.payoutId}>
                                    <td>{fmtDate(payout.confirmedAt)}</td>
                                    <td>{payout.label} — {payout.payeeName}</td>
                                    <td className="amount-coral col-right">{fmtEur(payout.amount)}</td>
                                    <td>
                                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => onViewBreakdown(payout.payoutId)}>
                                        {t('funds.viewBreakdown')}
                                      </button>
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          )}
                        </div>
                        <Donut
                          legend={false}
                          slices={sectionPayouts.map((payout, i) => ({
                            label: `${payout.label} — ${payout.payeeName}`,
                            value: payout.amount,
                            color: DONUT_PALETTE[i % DONUT_PALETTE.length],
                          }))}
                        />
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
