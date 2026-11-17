'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { ROUTES } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';
import { PaymentProviderId } from '@/types/paymentProvider';

/**
 * Builds the dismissal key, scoped by user **and** by the status pair it was dismissed for.
 *
 * The user id is required because `localStorage` is scoped to the browser, not to the account:
 * without it, dismissing the card as one association would hide it for every other account on
 * the same browser. The statuses are part of the key so that any onboarding progression — or a
 * regression such as a rejected dossier — re-arms the card instead of leaving the association
 * with a permanently hidden banner.
 */
function dismissedKey(userId: string, verificationStatus: VerificationStatus, bankStatus: BankSetupStatus): string {
  return `cl-acc-card-dismissed:${userId}:${verificationStatus}:${bankStatus}`;
}

/** Visual variant of a check row, mapped 1:1 to the `.acc-check.*` CSS modifiers. */
const CheckVariant = {
  TODO: 'todo',
  PENDING: 'pending',
  DONE: 'done',
  REJECTED: 'rejected',
} as const;
type CheckVariant = typeof CheckVariant[keyof typeof CheckVariant];

/** Where the row's call-to-action sends the association. */
const CheckAction = {
  /** "Vérification" tab of the settings page. */
  VERIF: 'VERIF',
  /** "Encaissement des dons" tab of the settings page. */
  BANK: 'BANK',
  /**
   * The payment provider's own page (`bankActionUrl`: Mollie hosted onboarding, or HelloAsso verification page),
   * in a new tab; falls back to the bank tab when the provider gave no link.
   */
  PROVIDER_ACTION: 'PROVIDER_ACTION',
} as const;
type CheckAction = typeof CheckAction[keyof typeof CheckAction];

interface RowSpec {
  variant: CheckVariant;
  /** Content of the round badge on the left of the row. */
  icon: string;
  /** Call-to-action of the row, null when none. */
  action: CheckAction | null;
  /** True when the row shows an ETA hint instead of a CTA. */
  hasEta: boolean;
}

/** One row per `VerificationStatus` value. */
const KYC_ROWS: Record<VerificationStatus, RowSpec> = {
  [VerificationStatus.UNVERIFIED]: { variant: CheckVariant.TODO, icon: '1', action: CheckAction.VERIF, hasEta: false },
  [VerificationStatus.PENDING]: { variant: CheckVariant.PENDING, icon: '⏳', action: null, hasEta: true },
  [VerificationStatus.VERIFIED]: { variant: CheckVariant.DONE, icon: '✓', action: null, hasEta: false },
  [VerificationStatus.REJECTED]: { variant: CheckVariant.REJECTED, icon: '!', action: CheckAction.VERIF, hasEta: false },
};

/** One row per `BankSetupStatus` value (Mollie, and the default while the provider is unknown). */
const BANK_ROWS: Record<BankSetupStatus, RowSpec> = {
  [BankSetupStatus.NOT_CONNECTED]: { variant: CheckVariant.TODO, icon: '2', action: CheckAction.BANK, hasEta: false },
  [BankSetupStatus.NEEDS_DATA]: { variant: CheckVariant.PENDING, icon: '⏳', action: CheckAction.PROVIDER_ACTION, hasEta: false },
  [BankSetupStatus.IN_REVIEW]: { variant: CheckVariant.PENDING, icon: '⏳', action: null, hasEta: true },
  [BankSetupStatus.COMPLETED]: { variant: CheckVariant.DONE, icon: '✓', action: null, hasEta: false },
  [BankSetupStatus.BROKEN]: { variant: CheckVariant.REJECTED, icon: '!', action: CheckAction.BANK, hasEta: false },
};

/**
 * HelloAsso rows: same as Mollie except IN_REVIEW, where HelloAsso gives no delay and may wait for documents the
 * association uploads itself — the row links to the tab (screen 5, "Mon dossier") instead of showing an ETA.
 * NEEDS_DATA is "suspended by HelloAsso" and opens the HelloAsso verification page.
 */
const HELLOASSO_BANK_ROWS: Record<BankSetupStatus, RowSpec> = {
  ...BANK_ROWS,
  [BankSetupStatus.IN_REVIEW]: { variant: CheckVariant.PENDING, icon: '⏳', action: CheckAction.BANK, hasEta: false },
};

interface AccountCompletionCardProps {
  verificationStatus: VerificationStatus;
  bankStatus: BankSetupStatus;
  /** Back-office reason shown on the KYC row when the dossier was rejected. */
  rejectionReason: string | null;
  /**
   * Payment-provider action link (Mollie hosted onboarding, or HelloAsso verification page), used by the NEEDS_DATA
   * call-to-action.
   */
  bankActionUrl: string | null;
  /** Payment provider of the association: HelloAsso gets its own bank-row wording; null/absent = Mollie wording. */
  paymentProvider?: PaymentProviderId | null;
}

/**
 * Account-completion banner shown on the association dashboard home page.
 *
 * Renders one row per setup step, each rendered according to its own status (four for KYC
 * verification, five for the bank account). Rows stay visible once complete — they turn green —
 * so the card doubles as a recap. Only the user's × dismisses it — for that account, on that
 * browser, and only as long as both statuses stay unchanged.
 */
export function AccountCompletionCard({
  verificationStatus,
  bankStatus,
  rejectionReason,
  bankActionUrl,
  paymentProvider,
}: AccountCompletionCardProps) {
  const helloAsso = paymentProvider === PaymentProviderId.HELLOASSO;
  const t = useTranslations('dashboard.association.home.accCard');
  const router = useRouter();
  const locale = useLocale();
  const userId = useAuthStore((s) => s.user?.id ?? null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    if (userId === null) return;
    setDismissed(localStorage.getItem(dismissedKey(userId, verificationStatus, bankStatus)) === '1');
  }, [userId, verificationStatus, bankStatus]);

  if (dismissed) return null;

  const kycRow = KYC_ROWS[verificationStatus];
  const bankRow = (helloAsso ? HELLOASSO_BANK_ROWS : BANK_ROWS)[bankStatus];
  const verified = verificationStatus === VerificationStatus.VERIFIED;
  const bankDone = bankStatus === BankSetupStatus.COMPLETED;
  const isPartial = (verified ? 1 : 0) + (bankDone ? 1 : 0) >= 1;

  const subKey = verificationStatus === VerificationStatus.REJECTED
    ? 'rejected'
    : verified && bankDone
      ? (helloAsso ? 'helloAsso.allDone' : 'allDone')
      : !verified
        ? 'notVerified'
        : (helloAsso ? 'helloAsso.bankOnly' : 'bankOnly');

  function handleCta(action: CheckAction | null) {
    if (action === null) return;
    if (action === CheckAction.PROVIDER_ACTION && bankActionUrl !== null) {
      window.open(bankActionUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    const tab = action === CheckAction.VERIF ? 'verif' : 'bank';
    router.push(`/${locale}${ROUTES.ASSOCIATION_PROFILE}?tab=${tab}`);
  }

  function handleDismiss() {
    if (userId !== null) localStorage.setItem(dismissedKey(userId, verificationStatus, bankStatus), '1');
    setDismissed(true);
  }

  // `key` is the React key; `labels` the i18n namespace of the row under `checks.`.
  const checks: { key: string; labels: string; status: string; spec: RowSpec; emoji: string; reason: string | null }[] = [
    { key: 'kyc', labels: 'kyc', status: verificationStatus, spec: kycRow, emoji: '📋', reason: rejectionReason },
    { key: 'bank', labels: helloAsso ? 'helloAssoBank' : 'bank', status: bankStatus, spec: bankRow, emoji: '🏦', reason: null },
  ];

  return (
    <div className={`acc-card${isPartial ? ' partial' : ''}`}>
      <div className="acc-card-head">
        <div>
          <div className="acc-card-title">{t('title')}</div>
          <div className="acc-card-sub">{t(`sub.${subKey}`)}</div>
        </div>
        <button className="acc-card-close" onClick={handleDismiss} title={t('dismiss')}>
          ×
        </button>
      </div>
      <div className="acc-checks">
        {checks.map(({ key, labels, status, spec, emoji, reason }) => {
          const base = `checks.${labels}.${status}`;
          const showReason = spec.variant === CheckVariant.REJECTED && reason !== null && reason !== '';
          return (
            <div key={key} className={`acc-check ${spec.variant}`}>
              <div className="acc-check-ic">{spec.icon}</div>
              <div className="acc-check-body">
                <div className="acc-check-title">
                  <span aria-hidden="true">{emoji}</span> <span>{t(`checks.${labels}.title`)}</span>
                </div>
                <div className="acc-check-desc">{t(`${base}.desc`)}</div>
                {showReason && <div className="acc-check-reason">{reason}</div>}
              </div>
              {spec.variant === CheckVariant.DONE && (
                <span className="acc-check-ok">{t('checks.validated')}</span>
              )}
              {spec.hasEta && <span className="acc-check-eta">{t(`${base}.eta`)}</span>}
              {spec.action !== null && (
                <button className="acc-check-cta" onClick={() => handleCta(spec.action)}>
                  {t(`${base}.cta`)}
                </button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
