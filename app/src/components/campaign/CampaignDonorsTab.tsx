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

const AVATAR_COLORS = ['var(--bright-teal)', 'var(--deep-indigo)', 'var(--warm-coral)', '#b37800'];

function getInitials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('');
}

function getAvatarBg(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) & 0xffff;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

function OnChainChip({ onChain }: { onChain: boolean }) {
  if (onChain) return <span className="chip green">✓ on-chain</span>;
  return <span className="chip yellow">⏳</span>;
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
    <div className="cm-card" style={{ marginTop: '10px', marginBottom: 0 }}>
      <div className="cm-card-title">
        💸 {t('tx.title')}
        <button
          type="button"
          className="cm-btn cm-btn-ghost cm-btn-sm"
          onClick={onClose}
          style={{ marginLeft: 'auto' }}
        >
          {t('tx.close')}
        </button>
      </div>
      <div className="d-row">
        <span className="d-key">{t('tx.date')}</span>
        <span className="d-val">{fmtDate(donation.createdAt)}</span>
      </div>
      <div className="d-row">
        <span className="d-key">{t('tx.title')}</span>
        <span className="d-val" style={{ fontFamily: "'Syne',sans-serif", fontWeight: 700, color: 'var(--teal-dark)' }}>
          {fmtEur(donation.amount)}
        </span>
      </div>
      <div className="d-row">
        <span className="d-key">{t('tx.ref')}</span>
        <code style={{ fontSize: '10px', background: 'var(--mist-lavender)', padding: '2px 6px', borderRadius: '4px' }}>
          {fmtRef(donation.providerRef)}
        </code>
      </div>
      <div className="d-row">
        <span className="d-key">{t('tx.onChain')}</span>
        <OnChainChip onChain={donation.onChain} />
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
    <div className="cm-card">
      <div className="cm-card-title">
        👤 {donor.displayName}
        <button
          type="button"
          className="cm-btn cm-btn-ghost cm-btn-sm"
          onClick={onClose}
          style={{ marginLeft: 'auto' }}
        >
          {t('detail.close')}
        </button>
      </div>

      <div className="cm-stats" style={{ marginBottom: '16px' }}>
        <div className="cm-stat">
          <div className="cm-stat-lbl">{t('detail.total')}</div>
          <div className="cm-stat-val val-dark">{fmtEur(donor.totalAmount)}</div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-lbl">{t('detail.txCount')}</div>
          <div className="cm-stat-val val-teal">{donor.txCount}</div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-lbl">{t('detail.lastDonation')}</div>
          <div className="cm-stat-val" style={{ fontSize: '14px' }}>{fmtDate(donor.lastDonationAt)}</div>
        </div>
      </div>

      <div className="d-section">{t('detail.transactions')}</div>

      {isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '16px' }}>
          <div className="w-[20px] h-[20px] rounded-full border-2 border-[var(--bright-teal)]/30 border-t-[var(--bright-teal)] animate-spin" />
        </div>
      ) : donations.length === 0 ? (
        <p style={{ fontSize: '12px', color: 'var(--slate-lavender)', padding: '8px 0' }}>
          {t('detail.noTx')}
        </p>
      ) : (
        donations.map((d) => (
          <div key={d.id}>
            <button
              type="button"
              onClick={() =>
                selectedDonation?.id === d.id ? onCloseDonation() : onSelectDonation(d)
              }
              className="d-row"
              style={{ width: '100%', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left' }}
            >
              <span className="d-key">{fmtDate(d.createdAt)}</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span style={{ fontFamily: "'Syne',sans-serif", fontWeight: 700, color: 'var(--teal-dark)' }}>
                  {fmtEur(d.amount)}
                </span>
                <OnChainChip onChain={d.onChain} />
              </span>
            </button>
            {selectedDonation?.id === d.id && (
              <DonationDetail donation={d} onClose={onCloseDonation} />
            )}
          </div>
        ))
      )}
    </div>
  );
}

function Pager({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (p: number) => void;
}) {
  if (totalPages <= 1) return null;

  const pages: (number | '…')[] = [];
  if (totalPages <= 7) {
    for (let i = 0; i < totalPages; i++) pages.push(i);
  } else {
    pages.push(0);
    if (page > 2) pages.push('…');
    for (let i = Math.max(1, page - 1); i <= Math.min(totalPages - 2, page + 1); i++) pages.push(i);
    if (page < totalPages - 3) pages.push('…');
    pages.push(totalPages - 1);
  }

  return (
    <div className="pager">
      <button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}>←</button>
      {pages.map((p, i) =>
        p === '…' ? (
          <button key={`ellipsis-${i}`} type="button" disabled style={{ cursor: 'default' }}>…</button>
        ) : (
          <button
            key={p}
            type="button"
            className={p === page ? 'active' : undefined}
            onClick={() => onPageChange(p as number)}
          >
            {(p as number) + 1}
          </button>
        )
      )}
      <button type="button" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>→</button>
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

  const topDonor =
    sort === DonorSort.AMOUNT && donors.length > 0 ? donors[0].displayName : '—';
  const avgAmount =
    donors.length > 0 ? donors.reduce((s, d) => s + d.totalAmount, 0) / donors.length : 0;

  function handleExportCsv() {
    if (donors.length === 0) return;
    const header = ['Nom', 'Montant (€)', 'Transactions', 'Dernier don'];
    const rows = donors.map((d) => [
      `"${d.displayName.replace(/"/g, '""')}"`,
      d.totalAmount.toFixed(2),
      String(d.txCount),
      fmtDate(d.lastDonationAt),
    ]);
    const csv = [header, ...rows].map((r) => r.join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `donateurs-${campaign.id}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div>
      {/* ── Stats ─────────────────────────────────────────────────────────── */}
      <div className="cm-stats">
        <div className="cm-stat">
          <div className="cm-stat-icon">👥</div>
          <div className="cm-stat-lbl">{t('stats.total')}</div>
          <div className="cm-stat-val val-teal">{isLoading ? '—' : totalElements}</div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">💎</div>
          <div className="cm-stat-lbl">{t('stats.avg')}</div>
          <div className="cm-stat-val val-dark">{donors.length > 0 ? fmtEur(avgAmount) : '—'}</div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">🏆</div>
          <div className="cm-stat-lbl">{t('stats.top')}</div>
          <div className="cm-stat-val val-amber">{isLoading ? '—' : topDonor}</div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">💶</div>
          <div className="cm-stat-lbl">{t('stats.raised')}</div>
          <div className="cm-stat-val val-dark">{fmtEur(campaign.raised ?? 0)}</div>
        </div>
      </div>

      {/* ── Carte table ───────────────────────────────────────────────────── */}
      <div className="cm-card">
        <div className="filter-bar">
          <input
            className="cm-fi"
            style={{ maxWidth: '260px' }}
            type="text"
            placeholder={t('search.placeholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select
            className="fsel"
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
          <span style={{ marginLeft: 'auto', fontSize: '12px', color: 'var(--slate-lavender)' }}>
            {!isLoading && t('showing', { count: totalElements })}
          </span>
          <button type="button" className="cm-btn cm-btn-ghost cm-btn-sm" onClick={handleExportCsv}>
            {t('exportCsv')}
          </button>
        </div>

        {isLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '32px' }}>
            <div className="w-[28px] h-[28px] rounded-full border-2 border-[var(--bright-teal)]/30 border-t-[var(--bright-teal)] animate-spin" />
          </div>
        ) : error ? (
          <p style={{ fontSize: '13px', color: 'var(--warm-coral)', textAlign: 'center', padding: '20px 0' }}>
            {t('error')}
          </p>
        ) : donors.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--slate-lavender)', textAlign: 'center', padding: '28px 0' }}>
            {t('empty')}
          </p>
        ) : (
          <div className="tw">
            <table className="cm-table">
              <thead>
                <tr>
                  <th>{t('table.donor')}</th>
                  <th style={{ textAlign: 'right' }}>{t('table.amount')}</th>
                  <th style={{ textAlign: 'center' }}>{t('table.transactions')}</th>
                  <th>{t('table.lastDonation')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {donors.map((donor) => (
                  <tr key={donor.donorId}>
                    <td>
                      <div className="avatar-row">
                        <div
                          className="avatar avatar-xs"
                          style={{ background: getAvatarBg(donor.donorId) }}
                        >
                          {getInitials(donor.displayName)}
                        </div>
                        <div style={{ fontWeight: 500 }}>{donor.displayName}</div>
                      </div>
                    </td>
                    <td style={{ textAlign: 'right', fontFamily: "'Syne',sans-serif", fontWeight: 700, color: 'var(--teal-dark)' }}>
                      {fmtEur(donor.totalAmount)}
                    </td>
                    <td style={{ textAlign: 'center', color: 'var(--bright-teal)', fontWeight: 600 }}>
                      {donor.txCount}
                    </td>
                    <td style={{ color: 'var(--slate-lavender)' }}>
                      {fmtDate(donor.lastDonationAt)}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="cm-btn cm-btn-ghost cm-btn-sm"
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

        <Pager page={page} totalPages={totalPages} onPageChange={setPage} />
      </div>

      {/* ── Donor detail panel ────────────────────────────────────────────── */}
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
