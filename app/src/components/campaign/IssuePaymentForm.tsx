'use client';

import { useState, useMemo, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { usePayees } from '@/hooks/payee/usePayees';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { fmtEur } from '@/components/campaign/payoutDisplay';
import { useToastStore } from '@/stores/toastStore';
import { getBlockingReasons } from '@/lib/api/payment';
import {
  PayoutKind,
  PayoutBlockingReason,
  isPayoutInFlight,
  needsBankAuthorisation,
} from '@/types/payment';
import { IbanVerificationStatus } from '@/types/payee';
import { ROUTES } from '@/lib/routes';
import type { CreatePayoutRequest, PayoutDto } from '@/types/payment';

interface Props {
  campaignId: string;
  /** Creates then confirms the payout, ordering the real transfer. */
  submit: (req: CreatePayoutRequest) => Promise<PayoutDto>;
  isSaving: boolean;
  /**
   * True while the backend would only simulate transfers. The button stays visible but disabled:
   * a payment reported as sent that never reached a bank is worse than one that cannot be issued.
   */
  paymentsDisabled: boolean;
}

const REMUNERATION_CODES = new Set(['64-rem', '64-soc']);

/** Preset accounting codes, grouped as offered in the type select. Each has a `typeCodes.*` label. */
const TYPE_CODE_GROUPS = {
  operational: ['60-mat', '60-svc', '61-loc', '61-ent', '62-tra', '62-pub'],
  personnel: ['64-rem', '64-soc'],
  other: ['65-ges'],
} as const;
const MIN_LABEL_LENGTH = 16;

const BLOCKING_REASON_LABEL_KEYS: Record<PayoutBlockingReason, string> = {
  INSUFFICIENT_BALANCE: 'insufficientBalance',
  DESCRIPTION_TOO_SHORT: 'descriptionTooShort',
};

function kindFromTypeCode(code: string) {
  return REMUNERATION_CODES.has(code) ? PayoutKind.REMUNERATION : PayoutKind.EXPENSE;
}

/**
 * Issuance form — left column of the Payments tab, beside the expense breakdown.
 *
 * Extracted from the tab so the journal below can be read on its own, with the same controls and
 * the same confirmation dialog it has always had. Its state is local: nothing outside needs a
 * half-typed payment.
 */
export function IssuePaymentForm({ campaignId, submit, isSaving, paymentsDisabled }: Props) {
  const t = useTranslations('dashboard.campaigns.payments');
  const router = useRouter();
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
    () => payees.filter((p) =>
      p.active
      && p.payeeType === (isRemunerationType ? 'PERSON' : 'COMPANY')
      && p.ibans.some((i) => i.status === IbanVerificationStatus.VERIFIED && i.active),
    ),
    [payees, isRemunerationType],
  );

  const selectedPayee = useMemo(() => filteredPayees.find((p) => p.id === payeeId), [filteredPayees, payeeId]);
  const verifiedIbans = useMemo(
    () => selectedPayee?.ibans.filter((i) => i.status === IbanVerificationStatus.VERIFIED && i.active) ?? [],
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
      getBlockingReasons(campaignId, payeeIbanId, amountNum, '')
        .then((reasons) => { if (!cancelled) setBlockingReasons(reasons); })
        .catch(() => { if (!cancelled) setBlockingReasons([]); });
    }, 300);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [campaignId, payeeIbanId, amountNum]);

  const isDescriptionTooShort = label.trim().length > 0 && label.trim().length < MIN_LABEL_LENGTH;

  const displayedBlockingReasons = useMemo(() => {
    const reasons: PayoutBlockingReason[] = blockingReasons.filter(
      (r) => r !== PayoutBlockingReason.DESCRIPTION_TOO_SHORT,
    );
    if (isDescriptionTooShort) reasons.push(PayoutBlockingReason.DESCRIPTION_TOO_SHORT);
    return reasons;
  }, [blockingReasons, isDescriptionTooShort]);

  const isValid = !paymentsDisabled && !!payeeId && !!payeeIbanId && !!effectiveTypeCode && amountNum > 0
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
    const verified = p?.ibans.filter((i) => i.status === IbanVerificationStatus.VERIFIED && i.active) ?? [];
    if (verified.length === 1) setPayeeIbanId(verified[0].id);
  }

  function handleAddPayee() {
    addToast('warning', 'addPayeeHint');
    router.push(ROUTES.ASSOCIATION_PAYEES);
  }

  async function handleConfirm() {
    setShowConfirm(false);
    try {
      const initiated = await submit({
        payeeId, payeeIbanId, amount: amountNum,
        kind: kindFromTypeCode(effectiveTypeCode),
        typeCode: effectiveTypeCode,
        label: label.trim(),
      });
      setPayeeId(''); setPayeeIbanId(''); setTypeCodeRaw('');
      setCustomTypeCode(''); setAmount(''); setLabel('');

      // The association is the debtor: nothing moves until it authorises the transfer with its own
      // bank. Send it straight there rather than reporting a payment that has not happened.
      if (needsBankAuthorisation(initiated) && initiated.bridgeCheckoutUrl) {
        addToast('success', 'paymentAwaitingBank');
        window.location.href = initiated.bridgeCheckoutUrl;
        return;
      }
      addToast('success', isPayoutInFlight(initiated) ? 'paymentSubmitted' : 'paymentSuccess');
    } catch {
      addToast('error', 'paymentError');
    }
  }

  return (
    <>
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
              {Object.entries(TYPE_CODE_GROUPS).map(([group, codes]) => (
                <optgroup key={group} label={t(`typeGroups.${group}`)}>
                  {codes.map((code) => (
                    <option key={code} value={code}>{t(`typeCodes.${code}`)}</option>
                  ))}
                  {group === 'other' && <option value="custom">{t('typeCodes.custom')}</option>}
                </optgroup>
              ))}
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

        {/* The title sits on the wrapper: a disabled button receives no pointer event, so its
            own tooltip would never show. */}
        <span
          className="block w-full"
          title={paymentsDisabled ? t('form.paymentsDisabled') : undefined}
        >
          <button
            className="cm-btn cm-btn-primary w-full"
            disabled={!isValid || isSaving}
            onClick={() => setShowConfirm(true)}
          >
            {isSaving ? '…' : t('form.submit')}
          </button>
        </span>

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

      <ConfirmDialog
        isOpen={showConfirm}
        variant="default"
        title={t('confirm.title')}
        message={t('confirm.message', { amount: fmtEur(amountNum), payee: selectedPayee?.name ?? '' })}
        confirmLabel={t('confirm.submit')}
        onConfirm={handleConfirm}
        onCancel={() => setShowConfirm(false)}
      />
    </>
  );
}
