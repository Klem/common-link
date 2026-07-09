'use client';

import { useState, useMemo, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import type { UsePaymentsReturn } from '@/hooks/campaign/usePayments';
import { usePayees } from '@/hooks/payee/usePayees';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Donut } from '@/components/ui/Donut';
import { useToastStore } from '@/stores/toastStore';
import { getBlockingReasons } from '@/lib/api/payment';
import { PayoutKind, PayoutStatus } from '@/types/payment';
import { IbanVerificationStatus } from '@/types/payee';
import { ROUTES } from '@/lib/routes';
import type { CampaignDto } from '@/types/campaign';
import { PayoutBlockingReason } from '@/types/payment';
import type { PayoutDto } from '@/types/payment';

interface Props {
  campaign: CampaignDto;
  payments: UsePaymentsReturn;
}

const REMUNERATION_CODES = new Set(['64-rem', '64-soc']);
const MIN_LABEL_LENGTH = 16;

const BLOCKING_REASON_LABEL_KEYS: Record<PayoutBlockingReason, string> = {
  INSUFFICIENT_BALANCE: 'insufficientBalance',
  DESCRIPTION_TOO_SHORT: 'descriptionTooShort',
};

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
    return <span className="pay-chip failed">✗</span>;
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
  const verifiedIbans = useMemo(
    () => selectedPayee?.ibans.filter((i) => i.status === IbanVerificationStatus.VERIFIED) ?? [],
    [selectedPayee],
  );
  const selectedIban = useMemo(
    () => verifiedIbans.find((i) => i.id === payeeIbanId),
    [verifiedIbans, payeeIbanId],
  );

  const amountNum = parseFloat(amount) || 0;

  const [blockingReasons, setBlockingReasons] = useState<PayoutBlockingReason[]>([]);

  useEffect(() => {
    if (!payeeIbanId || amountNum <= 0) {
      setBlockingReasons([]);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      getBlockingReasons(campaign.id, payeeIbanId, amountNum, '')
        .then((reasons) => { if (!cancelled) setBlockingReasons(reasons); })
        .catch(() => { if (!cancelled) setBlockingReasons([]); });
    }, 300);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [campaign.id, payeeIbanId, amountNum]);

  const isDescriptionTooShort = label.trim().length > 0 && label.trim().length < MIN_LABEL_LENGTH;

  const displayedBlockingReasons = useMemo(() => {
    const reasons: PayoutBlockingReason[] = blockingReasons.filter(
      (r) => r !== PayoutBlockingReason.DESCRIPTION_TOO_SHORT,
    );
    if (isDescriptionTooShort) reasons.push(PayoutBlockingReason.DESCRIPTION_TOO_SHORT);
    return reasons;
  }, [blockingReasons, isDescriptionTooShort]);

  const isValid = !!payeeId && !!payeeIbanId && !!effectiveTypeCode && amountNum > 0
    && label.trim().length >= MIN_LABEL_LENGTH && displayedBlockingReasons.length === 0;

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
    const verified = p?.ibans.filter((i) => i.status === IbanVerificationStatus.VERIFIED) ?? [];
    if (verified.length === 1) setPayeeIbanId(verified[0].id);
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
          <div className="cm-stat-val val-dark">
            {summary ? fmtEur(summary.availableBalance) : '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">📤</div>
          <div className="cm-stat-lbl">{t('stats.paid')}</div>
          <div className="cm-stat-val val-amber-color">
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
          <div className="cm-stat-val val-teal">
            {summary ? fmtEur(summary.pendingAmount) : '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">⚡</div>
          <div className="cm-stat-lbl">{t('stats.transactions')}</div>
          <div className="cm-stat-val val-teal">
            {summary?.txTotal ?? '—'}
          </div>
        </div>
        <div className="cm-stat">
          <div className="cm-stat-icon">💚</div>
          <div className="cm-stat-lbl">{t('stats.confirmed')}</div>
          <div className="cm-stat-val val-dark">
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
          <div className="row2 mb-14">
            <div>
              <label className="cm-label">
                {t('form.typeCode')} <span className="cm-required">*</span>
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
                  className="cm-fi mt-6"
                  type="text"
                  maxLength={50}
                  placeholder={t('form.customCodePlaceholder')}
                  value={customTypeCode}
                  onChange={(e) => setCustomTypeCode(e.target.value)}
                />
              )}
            </div>
            <div>
              <label className="cm-label">
                {t('form.amount')} <span className="cm-required">*</span>
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
          <div className="mb-14">
            <label className="cm-label">
              {t('form.payee')} <span className="cm-required">*</span>
            </label>
            <div className="form-inline-row">
              <select
                className="cm-fi flex-1"
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

            {/* No IBAN at all */}
            {selectedPayee && selectedPayee.ibans.length === 0 && (
              <p className="cm-field-error">{t('noIban')}</p>
            )}

            {/* Has IBAN(s) but none VERIFIED */}
            {selectedPayee && selectedPayee.ibans.length > 0 && verifiedIbans.length === 0 && (
              <p className="cm-field-error">{t('noVerifiedIban')}</p>
            )}

            {/* Single verified IBAN preview */}
            {selectedPayee && verifiedIbans.length === 1 && selectedIban && (
              <div className="bene-preview show">
                <div className="bene-preview-name">{selectedPayee.name}</div>
                <div className="bene-preview-iban">{selectedIban.iban}</div>
              </div>
            )}

            {/* Multi verified-IBAN select */}
            {selectedPayee && verifiedIbans.length > 1 && (
              <div className="mt-6">
                <label className="cm-label cm-label-sm">{t('ibanSelect')}</label>
                <select
                  className="cm-fi"
                  value={payeeIbanId}
                  onChange={(e) => setPayeeIbanId(e.target.value)}
                >
                  <option value="">— IBAN —</option>
                  {verifiedIbans.map((ib) => (
                    <option key={ib.id} value={ib.id}>{ib.iban}</option>
                  ))}
                </select>
                {selectedIban && (
                  <div className="bene-preview show">
                    <div className="bene-preview-name">{selectedPayee.name}</div>
                    <div className="bene-preview-iban">{selectedIban.iban}</div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Label / justificatif */}
          <div className="mb-14">
            <label className="cm-label">
              {t('form.label')} <span className="cm-required">*</span>
            </label>
            <textarea
              className="cm-fi cm-fi-h70"
              placeholder={t('form.labelPlaceholder')}
              maxLength={500}
              value={label}
              onChange={(e) => setLabel(e.target.value)}
            />
          </div>

          {/* Payment method (SEPA only) */}
          <div className="mb-18">
            <label className="pay-method-label">
              <input type="radio" name="pay-method" defaultChecked
                className="cm-accent-teal" readOnly />
              <div>
                <div className="pay-method-title">{t('form.method')}</div>
                <div className="cm-hint-sm">{t('form.methodSub')}</div>
              </div>
            </label>
          </div>

          <button
            className="cm-btn cm-btn-primary w-full"
            disabled={!isValid || isSaving}
            onClick={() => setShowConfirm(true)}
          >
            {isSaving ? '…' : t('form.submit')}
          </button>

          {displayedBlockingReasons.length > 0 && (
            <div className="blocking-reasons">
              {displayedBlockingReasons.map((reason) => (
                <span key={reason} className="badge badge-warning">
                  {t(`blocking.${BLOCKING_REASON_LABEL_KEYS[reason]}`)}
                </span>
              ))}
            </div>
          )}
        </div>

        {/* ── RIGHT: history + donut ───────────────────────────────── */}
        <div>
          {/* History */}
          <div className="cm-card mb-14">
            <div className="cm-card-title">{t('history.title')}</div>
            {isLoading ? (
              <div className="cm-loading-center">
                <div className="animate-spin rm-spinner lg" />
              </div>
            ) : error ? (
              <p className="cm-error-center">{error}</p>
            ) : payouts.length === 0 ? (
              <p className="cm-empty-center">{t('history.empty')}</p>
            ) : (
              payouts.map((p) => (
                <div key={p.id} className="pay-row">
                  <div className="pay-row-main">
                    <div className="pay-row-name">{p.payeeName}</div>
                    <div className="cm-hint-sm">
                      {p.typeCode} · {fmtDate(p.createdAt)}
                    </div>
                  </div>
                  <span
                    className="pay-row-amount"
                    style={{ color: p.status === PayoutStatus.CONFIRMED ? 'var(--teal-dark)' : '#b37800' }}
                  >
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
              <div className="cm-donut-center">
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
