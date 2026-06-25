'use client';

import { useState, useMemo } from 'react';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import type { UsePaymentsReturn } from '@/hooks/campaign/usePayments';
import { usePayees } from '@/hooks/payee/usePayees';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Donut } from '@/components/ui/Donut';
import { useToastStore } from '@/stores/toastStore';
import { PayoutKind, PayoutStatus } from '@/types/payment';
import { ROUTES } from '@/lib/routes';
import type { CampaignDto } from '@/types/campaign';
import type { PayoutDto } from '@/types/payment';

interface Props {
  campaign: CampaignDto;
  payments: UsePaymentsReturn;
}

const REMUNERATION_CODES = new Set(['64-rem', '64-soc']);

function kindFromTypeCode(code: string) {
  return REMUNERATION_CODES.has(code) ? PayoutKind.REMUNERATION : PayoutKind.EXPENSE;
}

function fmtEur(amount: number) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(amount);
}

function fmtDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short' }).format(new Date(iso));
}

function StatusChip({ status }: { status: PayoutDto['status'] }) {
  if (status === PayoutStatus.CONFIRMED) {
    return <span className="pay-chip confirmed">✓</span>;
  }
  if (status === PayoutStatus.FAILED) {
    return <span className="pay-chip" style={{ background: 'rgba(255,107,91,.12)', color: 'var(--warm-coral)' }}>✗</span>;
  }
  return <span className="pay-chip pending">⏳</span>;
}

export function CampaignPaymentsTab({ campaign, payments }: Props) {
  const t = useTranslations('dashboard.campaigns.payments');
  const router = useRouter();
  const { payouts, summary, isLoading, isSaving, error, submit } = payments;
  const { payees } = usePayees();
  const addToast = useToastStore((s) => s.addToast);

  const [payeeId, setPayeeId] = useState('');
  const [payeeIbanId, setPayeeIbanId] = useState('');
  const [typeCodeRaw, setTypeCodeRaw] = useState('');
  const [customTypeCode, setCustomTypeCode] = useState('');
  const [amount, setAmount] = useState('');
  const [label, setLabel] = useState('');
  const [showConfirm, setShowConfirm] = useState(false);

  const isCustomType = typeCodeRaw === 'custom';
  const effectiveTypeCode = isCustomType ? customTypeCode.trim() : typeCodeRaw;
  const isRemunerationType = REMUNERATION_CODES.has(effectiveTypeCode);

  const filteredPayees = useMemo(
    () => payees.filter((p) => p.active && p.payeeType === (isRemunerationType ? 'PERSON' : 'COMPANY')),
    [payees, isRemunerationType],
  );

  const selectedPayee = useMemo(() => filteredPayees.find((p) => p.id === payeeId), [filteredPayees, payeeId]);
  const selectedIban = useMemo(
    () => selectedPayee?.ibans.find((i) => i.id === payeeIbanId),
    [selectedPayee, payeeIbanId],
  );

  const amountNum = parseFloat(amount) || 0;
  const isValid = !!payeeId && !!payeeIbanId && !!effectiveTypeCode && amountNum > 0 && label.trim().length >= 6;

  function handleTypeChange(value: string) {
    const newIsRemu = REMUNERATION_CODES.has(value);
    if (newIsRemu !== isRemunerationType) {
      setPayeeId('');
      setPayeeIbanId('');
    }
    setTypeCodeRaw(value);
    setCustomTypeCode('');
  }

  function handlePayeeChange(id: string) {
    setPayeeId(id);
    setPayeeIbanId('');
    const p = filteredPayees.find((x) => x.id === id);
    if (p?.ibans.length === 1) setPayeeIbanId(p.ibans[0].id);
  }

  function handleAddPayee() {
    addToast('warning', 'addPayeeHint');
    router.push(ROUTES.ASSOCIATION_PAYEES);
  }

  async function handleConfirm() {
    setShowConfirm(false);
    try {
      await submit({
        payeeId, payeeIbanId, amount: amountNum,
        kind: kindFromTypeCode(effectiveTypeCode),
        typeCode: effectiveTypeCode,
        label: label.trim(),
      });
      setPayeeId(''); setPayeeIbanId(''); setTypeCodeRaw('');
      setCustomTypeCode(''); setAmount(''); setLabel('');
      addToast('success', 'paymentSuccess');
    } catch {
      addToast('error', 'paymentError');
    }
  }

  /* Breakdown slices for donut */
  const donutSlices = useMemo(() => {
    const confirmed = payouts.filter((p) => p.status === PayoutStatus.CONFIRMED);
    const totals: Record<string, number> = {};
    confirmed.forEach((p) => { totals[p.typeCode] = (totals[p.typeCode] ?? 0) + p.amount; });
    return Object.entries(totals)
      .sort((a, b) => b[1] - a[1])
      .map(([code, value]) => ({ label: code, value }));
  }, [payouts]);

  return (
    <div>
      {/* ── Stats ─────────────────────────────────────────────────── */}
      <div className="cm-stats">
        <div className="cm-stat">
          <div className="cm-stat-icon">💰</div>
          <div className="cm-stat-lbl">{t('stats.availableBalance')}</div>
          <div className="cm-stat-val" style={{ color: 'var(--teal-dark)' }}>
            {summary ? fmtEur(summary.availableBalance) : '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">📤</div>
          <div className="cm-stat-lbl">{t('stats.paid')}</div>
          <div className="cm-stat-val" style={{ color: '#b37800' }}>
            {summary ? fmtEur(summary.confirmedAmount) : '—'}
          </div>
          {summary && (
            <div className="cm-stat-sub">
              {summary.confirmedCount} pmt{summary.confirmedCount !== 1 ? 's' : ''}
            </div>
          )}
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">⏳</div>
          <div className="cm-stat-lbl">{t('stats.pending')}</div>
          <div className="cm-stat-val" style={{ color: 'var(--bright-teal)' }}>
            {summary ? fmtEur(summary.pendingAmount) : '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">⚡</div>
          <div className="cm-stat-lbl">{t('stats.transactions')}</div>
          <div className="cm-stat-val" style={{ color: 'var(--bright-teal)' }}>
            {summary?.txTotal ?? '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">💚</div>
          <div className="cm-stat-lbl">{t('stats.confirmed')}</div>
          <div className="cm-stat-val" style={{ color: 'var(--teal-dark)' }}>
            {summary?.txConfirmed ?? '—'}
          </div>
        </div>
      </div>

      {/* ── Two-column grid ───────────────────────────────────────── */}
      <div className="pay-form-grid">

        {/* ── LEFT: form ──────────────────────────────────────────── */}
        <div className="cm-card">
          <div className="cm-card-title">💸 {t('form.title')}</div>

          {/* Type + Amount row2 — FIRST */}
          <div className="row2" style={{ marginBottom: '14px' }}>
            <div>
              <label className="cm-label">
                {t('form.typeCode')} <span style={{ color: 'var(--warm-coral)' }}>*</span>
              </label>
              <select
                className="cm-fi"
                value={typeCodeRaw}
                onChange={(e) => handleTypeChange(e.target.value)}
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
                  className="cm-fi"
                  type="text"
                  maxLength={50}
                  placeholder={t('form.customCodePlaceholder')}
                  value={customTypeCode}
                  onChange={(e) => setCustomTypeCode(e.target.value)}
                  style={{ marginTop: '6px' }}
                />
              )}
            </div>
            <div>
              <label className="cm-label">
                {t('form.amount')} <span style={{ color: 'var(--warm-coral)' }}>*</span>
              </label>
              <input
                className="cm-fi"
                type="number"
                min="0.01"
                step="0.01"
                placeholder="0,00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
          </div>

          {/* Payee select + Add button — BELOW type/amount */}
          <div style={{ marginBottom: '14px' }}>
            <label className="cm-label">
              {t('form.payee')} <span style={{ color: 'var(--warm-coral)' }}>*</span>
            </label>
            <div style={{ display: 'flex', gap: '8px' }}>
              <select
                className="cm-fi"
                style={{ flex: 1 }}
                value={payeeId}
                onChange={(e) => handlePayeeChange(e.target.value)}
              >
                <option value="">{t('form.payeePlaceholder')}</option>
                {filteredPayees.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
              <button
                type="button"
                className="cm-btn cm-btn-ghost cm-btn-sm"
                onClick={handleAddPayee}
              >
                {t('form.addPayee')}
              </button>
            </div>

            {/* No IBAN warning */}
            {selectedPayee && selectedPayee.ibans.length === 0 && (
              <p style={{ fontSize: '12px', color: 'var(--warm-coral)', marginTop: '6px' }}>{t('noIban')}</p>
            )}

            {/* Single IBAN preview */}
            {selectedPayee && selectedPayee.ibans.length === 1 && selectedIban && (
              <div className="bene-preview" style={{ display: 'block' }}>
                <div style={{ fontWeight: 600 }}>{selectedPayee.name}</div>
                <div style={{ color: 'var(--slate-lavender)', marginTop: '2px' }}>{selectedIban.iban}</div>
              </div>
            )}

            {/* Multi-IBAN select */}
            {selectedPayee && selectedPayee.ibans.length > 1 && (
              <div style={{ marginTop: '6px' }}>
                <label className="cm-label" style={{ fontSize: '11px' }}>{t('ibanSelect')}</label>
                <select
                  className="cm-fi"
                  value={payeeIbanId}
                  onChange={(e) => setPayeeIbanId(e.target.value)}
                >
                  <option value="">— IBAN —</option>
                  {selectedPayee.ibans.map((ib) => (
                    <option key={ib.id} value={ib.id}>{ib.iban}</option>
                  ))}
                </select>
                {selectedIban && (
                  <div className="bene-preview" style={{ display: 'block' }}>
                    <div style={{ fontWeight: 600 }}>{selectedPayee.name}</div>
                    <div style={{ color: 'var(--slate-lavender)', marginTop: '2px' }}>{selectedIban.iban}</div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Label / justificatif */}
          <div style={{ marginBottom: '14px' }}>
            <label className="cm-label">
              {t('form.label')} <span style={{ color: 'var(--warm-coral)' }}>*</span>
            </label>
            <textarea
              className="cm-fi"
              placeholder={t('form.labelPlaceholder')}
              maxLength={500}
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              style={{ minHeight: '70px' }}
            />
          </div>

          {/* Payment method (SEPA only) */}
          <div style={{ marginBottom: '18px' }}>
            <label style={{
              display: 'flex', alignItems: 'center', gap: '9px',
              padding: '10px 12px',
              background: 'rgba(78,205,196,.05)',
              border: '1px solid rgba(78,205,196,.25)',
              borderRadius: 'var(--radius-md)',
              cursor: 'pointer', fontSize: '12.5px',
            }}>
              <input type="radio" name="pay-method" defaultChecked
                style={{ accentColor: 'var(--bright-teal)' }} readOnly />
              <div>
                <div style={{ fontWeight: 600 }}>{t('form.method')}</div>
                <div style={{ fontSize: '11px', color: 'var(--slate-lavender)' }}>{t('form.methodSub')}</div>
              </div>
            </label>
          </div>

          <button
            className="cm-btn cm-btn-primary"
            style={{ width: '100%' }}
            disabled={!isValid || isSaving}
            onClick={() => setShowConfirm(true)}
          >
            {isSaving ? '…' : t('form.submit')}
          </button>
        </div>

        {/* ── RIGHT: history + donut ───────────────────────────────── */}
        <div>
          {/* History */}
          <div className="cm-card" style={{ marginBottom: '14px' }}>
            <div className="cm-card-title">{t('history.title')}</div>
            {isLoading ? (
              <div style={{ display: 'flex', justifyContent: 'center', padding: '20px 0' }}>
                <div className="animate-spin" style={{ width: '24px', height: '24px', borderRadius: '50%', border: '2px solid rgba(78,205,196,.3)', borderTopColor: 'var(--bright-teal)' }} />
              </div>
            ) : error ? (
              <p style={{ fontSize: '13px', color: 'var(--warm-coral)', textAlign: 'center', padding: '16px 0' }}>{error}</p>
            ) : payouts.length === 0 ? (
              <p style={{ fontSize: '13px', color: 'var(--slate-lavender)', textAlign: 'center', padding: '20px 0' }}>{t('history.empty')}</p>
            ) : (
              payouts.map((p) => (
                <div key={p.id} className="pay-row">
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: '12px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.payeeName}</div>
                    <div style={{ fontSize: '11px', color: 'var(--slate-lavender)' }}>
                      {p.typeCode} · {fmtDate(p.createdAt)}
                    </div>
                  </div>
                  <span style={{
                    fontWeight: 700, fontFamily: "'Syne', sans-serif",
                    color: p.status === PayoutStatus.CONFIRMED ? 'var(--teal-dark)' : '#b37800',
                  }}>
                    {fmtEur(p.amount)}
                  </span>
                  <StatusChip status={p.status} />
                </div>
              ))
            )}
          </div>

          {/* Donut breakdown */}
          {donutSlices.length > 0 && (
            <div className="cm-card">
              <div className="cm-card-title">{t('breakdown.title')}</div>
              <div style={{ display: 'flex', justifyContent: 'center', padding: '16px 0' }}>
                <Donut slices={donutSlices} emptyKey="campaigns.payments.breakdown.empty" />
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
        message={t('confirm.message', { amount: fmtEur(amountNum), payee: selectedPayee?.name ?? '' })}
        confirmLabel={t('confirm.submit')}
        onConfirm={handleConfirm}
        onCancel={() => setShowConfirm(false)}
      />
    </div>
  );
}
