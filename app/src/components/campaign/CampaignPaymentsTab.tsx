'use client';

import { useState, useMemo } from 'react';
import { useTranslations } from 'next-intl';
import { usePayments } from '@/hooks/campaign/usePayments';
import { usePayees } from '@/hooks/payee/usePayees';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { useToastStore } from '@/stores/toastStore';
import { PayoutKind, PayoutStatus } from '@/types/payment';
import type { CampaignDto } from '@/types/campaign';
import type { PayoutDto } from '@/types/payment';

interface Props {
  campaign: CampaignDto;
}

/** Plan comptable type codes that map to REMUNERATION kind. */
const REMUNERATION_CODES = new Set(['64-rem', '64-soc']);

/** Derives PayoutKind from a plan-comptable typeCode. */
function kindFromTypeCode(code: string) {
  return REMUNERATION_CODES.has(code) ? PayoutKind.REMUNERATION : PayoutKind.EXPENSE;
}

function fmtEur(amount: number) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short' }).format(
    new Date(iso),
  );
}

function StatusChip({ status }: { status: PayoutDto['status'] }) {
  if (status === PayoutStatus.CONFIRMED) {
    return (
      <span className="badge-active text-[11px] px-[8px] py-[2px] rounded-full font-semibold">
        ✓
      </span>
    );
  }
  if (status === PayoutStatus.FAILED) {
    return (
      <span className="badge-draft text-[11px] px-[8px] py-[2px] rounded-full font-semibold text-[var(--warm-coral)]">
        ✗
      </span>
    );
  }
  return (
    <span className="badge-draft text-[11px] px-[8px] py-[2px] rounded-full font-semibold text-[var(--soft-amber)]">
      ⏳
    </span>
  );
}

export function CampaignPaymentsTab({ campaign }: Props) {
  const t = useTranslations('dashboard.campaigns.payments');
  const { payouts, summary, isLoading, isSaving, error, submit } = usePayments(campaign.id);
  const { payees } = usePayees();
  const addToast = useToastStore((s) => s.addToast);

  /* Form state */
  const [payeeId, setPayeeId] = useState('');
  const [payeeIbanId, setPayeeIbanId] = useState('');
  const [typeCodeRaw, setTypeCodeRaw] = useState('');
  const [customTypeCode, setCustomTypeCode] = useState('');
  const [amount, setAmount] = useState('');
  const [label, setLabel] = useState('');
  const [showConfirm, setShowConfirm] = useState(false);

  const selectedPayee = useMemo(() => payees.find((p) => p.id === payeeId), [payees, payeeId]);
  const selectedIban = useMemo(
    () => selectedPayee?.ibans.find((i) => i.id === payeeIbanId),
    [selectedPayee, payeeIbanId],
  );

  const isCustomType = typeCodeRaw === 'custom';
  const effectiveTypeCode = isCustomType ? customTypeCode.trim() : typeCodeRaw;

  /* Validation (mirrors backend constraints) */
  const amountNum = parseFloat(amount) || 0;
  const isValid =
    !!payeeId &&
    !!payeeIbanId &&
    !!effectiveTypeCode &&
    amountNum > 0 &&
    label.trim().length >= 6;

  function handlePayeeChange(id: string) {
    setPayeeId(id);
    setPayeeIbanId('');
    const p = payees.find((x) => x.id === id);
    if (p?.ibans.length === 1) setPayeeIbanId(p.ibans[0].id);
  }

  async function handleConfirm() {
    setShowConfirm(false);
    try {
      await submit({
        payeeId,
        payeeIbanId,
        amount: amountNum,
        kind: kindFromTypeCode(effectiveTypeCode),
        typeCode: effectiveTypeCode,
        label: label.trim(),
      });
      setPayeeId('');
      setPayeeIbanId('');
      setTypeCodeRaw('');
      setCustomTypeCode('');
      setAmount('');
      setLabel('');
      addToast('success', 'dashboard.campaigns.payments.toast.success');
    } catch {
      addToast('error', 'dashboard.campaigns.payments.toast.error');
    }
  }

  /* Breakdown: sum confirmed amounts by typeCode */
  const breakdown = useMemo(() => {
    const confirmedPayouts = payouts.filter((p) => p.status === PayoutStatus.CONFIRMED);
    const totals: Record<string, number> = {};
    confirmedPayouts.forEach((p) => {
      totals[p.typeCode] = (totals[p.typeCode] ?? 0) + p.amount;
    });
    const totalAmt = Object.values(totals).reduce((s, v) => s + v, 0);
    return Object.entries(totals)
      .sort((a, b) => b[1] - a[1])
      .map(([code, amt]) => ({ code, amt, pct: totalAmt > 0 ? (amt / totalAmt) * 100 : 0 }));
  }, [payouts]);

  return (
    <div>
      {/* ── Stats bar ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-[12px] mb-[24px]">
        <div className="stat-card">
          <div className="stat-card-icon">💰</div>
          <div className="stat-card-label">{t('stats.availableBalance')}</div>
          <div className="stat-card-value" style={{ color: 'var(--teal-dark)' }}>
            {summary ? fmtEur(summary.availableBalance) : '—'}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card-icon">📤</div>
          <div className="stat-card-label">{t('stats.paid')}</div>
          <div className="stat-card-value" style={{ color: '#b37800' }}>
            {summary ? fmtEur(summary.confirmedAmount) : '—'}
          </div>
          {summary && (
            <div className="stat-card-sub">
              {summary.confirmedCount} pmt{summary.confirmedCount !== 1 ? 's' : ''}
            </div>
          )}
        </div>
        <div className="stat-card">
          <div className="stat-card-icon">⏳</div>
          <div className="stat-card-label">{t('stats.pending')}</div>
          <div className="stat-card-value" style={{ color: 'var(--bright-teal)' }}>
            {summary ? fmtEur(summary.pendingAmount) : '—'}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card-icon">⚡</div>
          <div className="stat-card-label">{t('stats.transactions')}</div>
          <div className="stat-card-value" style={{ color: 'var(--bright-teal)' }}>
            {summary?.txTotal ?? '—'}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card-icon">💚</div>
          <div className="stat-card-label">{t('stats.confirmed')}</div>
          <div className="stat-card-value" style={{ color: 'var(--teal-dark)' }}>
            {summary?.txConfirmed ?? '—'}
          </div>
        </div>
      </div>

      {/* ── Two-column grid ───────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-[16px]">

        {/* ── LEFT: form ──────────────────────────────────────────── */}
        <div className="card card-no-hover">
          <div className="card-header">💸 {t('form.title')}</div>
          <div className="card-body">

            {/* Payee select */}
            <div className="form-group mb-[14px]">
              <label className="form-label">
                {t('form.payee')} <span className="text-[var(--warm-coral)]">*</span>
              </label>
              <div className="flex gap-[8px]">
                <select
                  className="form-input flex-1"
                  value={payeeId}
                  onChange={(e) => handlePayeeChange(e.target.value)}
                >
                  <option value="">{t('form.payeePlaceholder')}</option>
                  {payees.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>
              {/* IBAN preview / select */}
              {selectedPayee && selectedPayee.ibans.length === 0 && (
                <p className="text-[12px] text-[var(--warm-coral)] mt-[6px]">{t('noIban')}</p>
              )}
              {selectedPayee && selectedPayee.ibans.length === 1 && selectedIban && (
                <div className="mt-[6px] p-[8px] rounded-[var(--radius-md)] bg-[var(--soft-cream)] text-[12px]">
                  <div className="font-semibold text-[var(--ink-navy)]">{selectedPayee.name}</div>
                  <div className="text-[var(--slate-lavender)] font-mono mt-[2px]">
                    {selectedIban.iban}
                  </div>
                </div>
              )}
              {selectedPayee && selectedPayee.ibans.length > 1 && (
                <div className="mt-[6px]">
                  <label className="form-label text-[11px]">{t('ibanSelect')}</label>
                  <select
                    className="form-input"
                    value={payeeIbanId}
                    onChange={(e) => setPayeeIbanId(e.target.value)}
                  >
                    <option value="">— IBAN —</option>
                    {selectedPayee.ibans.map((ib) => (
                      <option key={ib.id} value={ib.id}>
                        {ib.iban}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>

            {/* Type + Amount row */}
            <div className="grid grid-cols-2 gap-[12px] mb-[14px]">
              <div className="form-group">
                <label className="form-label">
                  {t('form.typeCode')} <span className="text-[var(--warm-coral)]">*</span>
                </label>
                <select
                  className="form-input"
                  value={typeCodeRaw}
                  onChange={(e) => {
                    setTypeCodeRaw(e.target.value);
                    setCustomTypeCode('');
                  }}
                >
                  <option value="">{t('form.typeCodePlaceholder')}</option>
                  <optgroup label={t('typeGroups.operational')}>
                    <option value="60-mat">{t('typeCodes.60-mat')}</option>
                    <option value="60-svc">{t('typeCodes.60-svc')}</option>
                    <option value="61-loc">{t('typeCodes.61-loc')}</option>
                    <option value="61-ent">{t('typeCodes.61-ent')}</option>
                    <option value="62-tra">{t('typeCodes.62-tra')}</option>
                    <option value="62-pub">{t('typeCodes.62-pub')}</option>
                  </optgroup>
                  <optgroup label={t('typeGroups.personnel')}>
                    <option value="64-rem">{t('typeCodes.64-rem')}</option>
                    <option value="64-soc">{t('typeCodes.64-soc')}</option>
                  </optgroup>
                  <optgroup label={t('typeGroups.other')}>
                    <option value="65-ges">{t('typeCodes.65-ges')}</option>
                    <option value="custom">{t('typeCodes.custom')}</option>
                  </optgroup>
                </select>
                {isCustomType && (
                  <input
                    className="form-input mt-[6px]"
                    type="text"
                    maxLength={50}
                    placeholder={t('form.customCodePlaceholder')}
                    value={customTypeCode}
                    onChange={(e) => setCustomTypeCode(e.target.value)}
                  />
                )}
              </div>
              <div className="form-group">
                <label className="form-label">
                  {t('form.amount')} <span className="text-[var(--warm-coral)]">*</span>
                </label>
                <input
                  className="form-input"
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="0,00"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                />
              </div>
            </div>

            {/* Label textarea */}
            <div className="form-group mb-[14px]">
              <label className="form-label">
                {t('form.label')} <span className="text-[var(--warm-coral)]">*</span>
              </label>
              <textarea
                className="form-input min-h-[70px]"
                placeholder={t('form.labelPlaceholder')}
                maxLength={500}
                value={label}
                onChange={(e) => setLabel(e.target.value)}
              />
            </div>

            {/* Payment method (SEPA only) */}
            <div className="mb-[18px]">
              <label className="flex items-center gap-[9px] p-[10px_12px] bg-[rgba(78,205,196,.05)] border border-[rgba(78,205,196,.25)] rounded-[var(--radius-md)] cursor-pointer text-[12.5px]">
                <input
                  type="radio"
                  name="pay-method"
                  defaultChecked
                  className="accent-[var(--bright-teal)]"
                  readOnly
                />
                <div>
                  <div className="font-semibold">{t('form.method')}</div>
                  <div className="text-[11px] text-[var(--slate-lavender)]">{t('form.methodSub')}</div>
                </div>
              </label>
            </div>

            <button
              className="btn btn-primary w-full"
              disabled={!isValid || isSaving}
              onClick={() => setShowConfirm(true)}
            >
              {isSaving ? '…' : t('form.submit')}
            </button>
          </div>
        </div>

        {/* ── RIGHT: history + breakdown ───────────────────────────── */}
        <div className="flex flex-col gap-[14px]">

          {/* History */}
          <div className="card card-no-hover flex-1">
            <div className="card-header">{t('history.title')}</div>
            <div className="card-body">
              {isLoading ? (
                <div className="flex justify-center py-[20px]">
                  <div className="w-[24px] h-[24px] rounded-full border-2 border-[var(--bright-teal)]/30 border-t-[var(--bright-teal)] animate-spin" />
                </div>
              ) : error ? (
                <p className="text-[13px] text-[var(--warm-coral)] text-center py-[16px]">{error}</p>
              ) : payouts.length === 0 ? (
                <p className="text-[13px] text-[var(--slate-lavender)] text-center py-[20px]">
                  {t('history.empty')}
                </p>
              ) : (
                <div className="flex flex-col gap-[1px]">
                  {[...payouts].map((p) => (
                    <div
                      key={p.id}
                      className="flex items-center gap-[10px] py-[10px] border-b border-[var(--color-border)] last:border-0"
                    >
                      <div className="flex-1 min-w-0">
                        <div className="font-semibold text-[12px] truncate">{p.payeeName}</div>
                        <div className="text-[11px] text-[var(--slate-lavender)]">
                          {p.typeCode} · {fmtDate(p.createdAt)}
                        </div>
                      </div>
                      <span
                        className="font-bold font-['Syne']"
                        style={{
                          color:
                            p.status === PayoutStatus.CONFIRMED
                              ? 'var(--teal-dark)'
                              : '#b37800',
                        }}
                      >
                        {fmtEur(p.amount)}
                      </span>
                      <StatusChip status={p.status} />
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Breakdown */}
          {breakdown.length > 0 && (
            <div className="card card-no-hover">
              <div className="card-header">{t('breakdown.title')}</div>
              <div className="card-body">
                <div className="flex flex-col gap-[8px]">
                  {breakdown.map(({ code, amt, pct }) => (
                    <div key={code}>
                      <div className="flex justify-between text-[11px] mb-[3px]">
                        <span className="text-[var(--slate-lavender)]">{code}</span>
                        <span className="font-semibold text-[var(--ink-navy)]">{fmtEur(amt)}</span>
                      </div>
                      <div className="h-[6px] rounded-full bg-[var(--mist-lavender)] overflow-hidden">
                        <div
                          className="h-full rounded-full bg-[var(--bright-teal)]"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Confirm dialog ────────────────────────────────────────── */}
      <ConfirmDialog
        isOpen={showConfirm}
        variant="default"
        title={t('confirm.title')}
        message={t('confirm.message', {
          amount: fmtEur(amountNum),
          payee: selectedPayee?.name ?? '',
        })}
        confirmLabel={t('confirm.submit')}
        onConfirm={handleConfirm}
        onCancel={() => setShowConfirm(false)}
      />
    </div>
  );
}
