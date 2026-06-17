'use client';

import { useTranslations } from 'next-intl';
import { useCampaignDonors } from '@/hooks/campaign/useCampaignDonors';
import { DonorSort } from '@/types/donor-campaign';
import type { CampaignDonorDto, DonationDto } from '@/types/donor-campaign';
import type { CampaignDto } from '@/types/campaign';

interface Props {
  campaign: CampaignDto;
}

function fmtEur(amount: number) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string | null) {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short' }).format(new Date(iso));
}

function fmtRef(ref: string) {
  return ref.length > 24 ? `${ref.slice(0, 12)}…${ref.slice(-8)}` : ref;
}

function OnChainChip({ onChain }: { onChain: boolean }) {
  if (onChain) {
    return (
      <span className="badge-active text-[10px] px-[6px] py-[1px] rounded-full font-semibold">
        ✓ on-chain
      </span>
    );
  }
  return (
    <span className="badge-draft text-[10px] px-[6px] py-[1px] rounded-full font-semibold text-[var(--soft-amber)]">
      ⏳
    </span>
  );
}

function DonationDetail({
  donation,
  onClose,
}: {
  donation: DonationDto;
  onClose: () => void;
}) {
  const t = useTranslations('dashboard.campaigns.donors');
  return (
    <div className="mt-[12px] p-[14px] rounded-[var(--radius-md)] bg-[var(--color-bg-2)] border border-[var(--color-border)]">
      <div className="flex items-center justify-between mb-[10px]">
        <span className="font-semibold text-[13px]">💸 {t('tx.title')}</span>
        <button
          type="button"
          className="btn btn-ghost text-[11px] py-[2px] px-[8px]"
          onClick={onClose}
        >
          {t('tx.close')}
        </button>
      </div>
      <div className="flex flex-col gap-[6px] text-[12px]">
        <div className="flex justify-between">
          <span className="text-[var(--slate-lavender)]">{t('tx.date')}</span>
          <span className="font-semibold">{fmtDate(donation.createdAt)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-[var(--slate-lavender)]">{t('tx.title')}</span>
          <span className="font-bold text-[var(--teal-dark)]">{fmtEur(donation.amount)}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-[var(--slate-lavender)]">{t('tx.ref')}</span>
          <code className="text-[10px] text-[var(--ink-navy)] bg-[var(--mist-lavender)] px-[6px] py-[1px] rounded">
            {fmtRef(donation.providerRef)}
          </code>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-[var(--slate-lavender)]">{t('tx.onChain')}</span>
          <OnChainChip onChain={donation.onChain} />
        </div>
      </div>
    </div>
  );
}

function DonorDetail({
  donor,
  donations,
  isLoading,
  selectedDonation,
  onSelectDonation,
  onCloseDonation,
  onClose,
}: {
  donor: CampaignDonorDto;
  donations: DonationDto[];
  isLoading: boolean;
  selectedDonation: DonationDto | null;
  onSelectDonation: (d: DonationDto) => void;
  onCloseDonation: () => void;
  onClose: () => void;
}) {
  const t = useTranslations('dashboard.campaigns.donors');
  return (
    <div className="card card-no-hover mt-[16px]">
      <div className="card-header flex items-center justify-between">
        <span>👤 {donor.displayName}</span>
        <button
          type="button"
          className="btn btn-ghost text-[11px] py-[2px] px-[8px]"
          onClick={onClose}
        >
          {t('detail.close')}
        </button>
      </div>
      <div className="card-body">
        {/* Donor summary */}
        <div className="grid grid-cols-3 gap-[10px] mb-[16px]">
          <div className="stat-card">
            <div className="stat-card-label">{t('detail.total')}</div>
            <div className="stat-card-value" style={{ color: 'var(--teal-dark)', fontSize: '16px' }}>
              {fmtEur(donor.totalAmount)}
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-card-label">{t('detail.txCount')}</div>
            <div className="stat-card-value" style={{ color: 'var(--bright-teal)', fontSize: '16px' }}>
              {donor.txCount}
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-card-label">{t('detail.lastDonation')}</div>
            <div className="stat-card-value" style={{ fontSize: '14px' }}>
              {fmtDate(donor.lastDonationAt)}
            </div>
          </div>
        </div>

        {/* Donations list */}
        <div className="font-semibold text-[12px] text-[var(--slate-lavender)] mb-[8px]">
          {t('detail.transactions')}
        </div>
        {isLoading ? (
          <div className="flex justify-center py-[16px]">
            <div className="w-[20px] h-[20px] rounded-full border-2 border-[var(--bright-teal)]/30 border-t-[var(--bright-teal)] animate-spin" />
          </div>
        ) : donations.length === 0 ? (
          <p className="text-[12px] text-[var(--slate-lavender)] py-[8px]">{t('detail.noTx')}</p>
        ) : (
          <div className="flex flex-col gap-[1px]">
            {donations.map((d) => (
              <div key={d.id}>
                <button
                  type="button"
                  onClick={() =>
                    selectedDonation?.id === d.id ? onCloseDonation() : onSelectDonation(d)
                  }
                  className="w-full flex items-center gap-[10px] py-[8px] border-b border-[var(--color-border)] last:border-0 text-left hover:bg-[var(--color-bg-2)] rounded px-[4px] transition-colors"
                >
                  <div className="flex-1 min-w-0">
                    <div className="font-bold text-[12px] text-[var(--teal-dark)]">
                      {fmtEur(d.amount)}
                    </div>
                    <div className="text-[11px] text-[var(--slate-lavender)]">
                      {fmtDate(d.createdAt)}
                    </div>
                  </div>
                  <OnChainChip onChain={d.onChain} />
                </button>
                {selectedDonation?.id === d.id && (
                  <DonationDetail
                    donation={d}
                    onClose={onCloseDonation}
                  />
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export function CampaignDonorsTab({ campaign }: Props) {
  const t = useTranslations('dashboard.campaigns.donors');
  const {
    donorsPage,
    page,
    search,
    sort,
    isLoading,
    error,
    selectedDonor,
    donorDonations,
    isDonorLoading,
    selectedDonation,
    setPage,
    setSearch,
    setSort,
    selectDonor,
    closeDonor,
    selectDonation,
    closeDonation,
  } = useCampaignDonors(campaign.id);

  const donors = donorsPage?.content ?? [];
  const totalElements = donorsPage?.totalElements ?? 0;
  const totalPages = donorsPage?.totalPages ?? 0;

  /* Stats derived from current page */
  const topDonor =
    sort === DonorSort.AMOUNT && donors.length > 0 ? donors[0].displayName : '—';
  const avgAmount =
    donors.length > 0
      ? donors.reduce((s, d) => s + d.totalAmount, 0) / donors.length
      : 0;

  return (
    <div>
      {/* ── Stats bar ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-[12px] mb-[24px]">
        <div className="stat-card">
          <div className="stat-card-icon">👥</div>
          <div className="stat-card-label">{t('stats.total')}</div>
          <div className="stat-card-value" style={{ color: 'var(--bright-teal)' }}>
            {isLoading ? '—' : totalElements}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card-icon">💎</div>
          <div className="stat-card-label">{t('stats.avg')}</div>
          <div className="stat-card-value" style={{ color: 'var(--teal-dark)' }}>
            {donors.length > 0 ? fmtEur(avgAmount) : '—'}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card-icon">🏆</div>
          <div className="stat-card-label">{t('stats.top')}</div>
          <div className="stat-card-value" style={{ color: '#b37800', fontSize: '14px' }}>
            {isLoading ? '—' : topDonor}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card-icon">💶</div>
          <div className="stat-card-label">{t('stats.raised')}</div>
          <div className="stat-card-value" style={{ color: 'var(--teal-dark)' }}>
            {fmtEur(campaign.raised ?? 0)}
          </div>
        </div>
      </div>

      {/* ── Filter bar + table ─────────────────────────────────────── */}
      <div className="card card-no-hover">
        <div className="card-body">
          {/* Filter bar */}
          <div className="flex flex-wrap gap-[10px] items-center mb-[16px]">
            <input
              className="form-input"
              style={{ maxWidth: '260px' }}
              type="text"
              placeholder={t('search.placeholder')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <select
              className="form-input"
              style={{ width: 'auto' }}
              value={sort}
              onChange={(e) => {
                setSort(e.target.value);
                setPage(0);
              }}
            >
              <option value={DonorSort.AMOUNT}>{t('sort.amount')}</option>
              <option value={DonorSort.DATE}>{t('sort.date')}</option>
              <option value={DonorSort.NAME}>{t('sort.name')}</option>
            </select>
            <span className="text-[12px] text-[var(--slate-lavender)] ml-auto">
              {!isLoading && t('showing', { count: totalElements })}
            </span>
          </div>

          {/* Table */}
          {isLoading ? (
            <div className="flex justify-center py-[32px]">
              <div className="w-[28px] h-[28px] rounded-full border-2 border-[var(--bright-teal)]/30 border-t-[var(--bright-teal)] animate-spin" />
            </div>
          ) : error ? (
            <p className="text-[13px] text-[var(--warm-coral)] text-center py-[20px]">
              {t('error')}
            </p>
          ) : donors.length === 0 ? (
            <p className="text-[13px] text-[var(--slate-lavender)] text-center py-[28px]">
              {t('empty')}
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-[13px]">
                <thead>
                  <tr className="border-b border-[var(--color-border)]">
                    <th className="text-left py-[8px] pr-[12px] font-semibold text-[var(--slate-lavender)]">
                      {t('table.donor')}
                    </th>
                    <th className="text-right py-[8px] pr-[12px] font-semibold text-[var(--slate-lavender)]">
                      {t('table.amount')}
                    </th>
                    <th className="text-center py-[8px] pr-[12px] font-semibold text-[var(--slate-lavender)]">
                      {t('table.transactions')}
                    </th>
                    <th className="text-left py-[8px] pr-[12px] font-semibold text-[var(--slate-lavender)]">
                      {t('table.lastDonation')}
                    </th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {donors.map((donor) => (
                    <tr
                      key={donor.donorId}
                      className="border-b border-[var(--color-border)] last:border-0"
                    >
                      <td className="py-[10px] pr-[12px]">
                        <span className="font-semibold">{donor.displayName}</span>
                      </td>
                      <td className="py-[10px] pr-[12px] text-right font-bold text-[var(--teal-dark)]">
                        {fmtEur(donor.totalAmount)}
                      </td>
                      <td className="py-[10px] pr-[12px] text-center text-[var(--slate-lavender)]">
                        {donor.txCount}
                      </td>
                      <td className="py-[10px] pr-[12px] text-[var(--slate-lavender)]">
                        {fmtDate(donor.lastDonationAt)}
                      </td>
                      <td className="py-[10px]">
                        <button
                          type="button"
                          className="btn btn-ghost text-[11px] py-[2px] px-[8px]"
                          onClick={() =>
                            selectedDonor?.donorId === donor.donorId
                              ? closeDonor()
                              : selectDonor(donor)
                          }
                        >
                          {t('table.view')}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Pager */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-[8px] mt-[16px]">
              <button
                type="button"
                className="btn btn-ghost text-[12px] py-[4px] px-[10px]"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                ←
              </button>
              <span className="text-[12px] text-[var(--slate-lavender)]">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                className="btn btn-ghost text-[12px] py-[4px] px-[10px]"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
              >
                →
              </button>
            </div>
          )}
        </div>
      </div>

      {/* ── Donor detail panel ─────────────────────────────────────── */}
      {selectedDonor && (
        <DonorDetail
          donor={selectedDonor}
          donations={donorDonations}
          isLoading={isDonorLoading}
          selectedDonation={selectedDonation}
          onSelectDonation={selectDonation}
          onCloseDonation={closeDonation}
          onClose={closeDonor}
        />
      )}
    </div>
  );
}
