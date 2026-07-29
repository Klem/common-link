'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { ROUTES } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';
import { VerificationStatus } from '@/types/association';
import { BankSetupStatus } from '@/lib/bankSetupStatus';

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
type CheckVariant = 'todo' | 'pending' | 'done' | 'rejected';

/** Where the row's call-to-action sends the association, if any. */
type CheckAction = 'verif' | 'bank' | 'mollie' | null;

interface RowSpec {
  variant: CheckVariant;
  /** Content of the round badge on the left of the row. */
  icon: string;
  action: CheckAction;
  /** True when the row shows an ETA hint instead of a CTA. */
  hasEta: boolean;
}

/** One row per `VerificationStatus` value. */
const KYC_ROWS: Record<VerificationStatus, RowSpec> = {
  [VerificationStatus.UNVERIFIED]: { variant: 'todo', icon: '1', action: 'verif', hasEta: false },
  [VerificationStatus.PENDING]: { variant: 'pending', icon: '⏳', action: null, hasEta: true },
  [VerificationStatus.VERIFIED]: { variant: 'done', icon: '✓', action: null, hasEta: false },
  [VerificationStatus.REJECTED]: { variant: 'rejected', icon: '!', action: 'verif', hasEta: false },
};

/** One row per `BankSetupStatus` value. */
const BANK_ROWS: Record<BankSetupStatus, RowSpec> = {
  [BankSetupStatus.NOT_CONNECTED]: { variant: 'todo', icon: '2', action: 'bank', hasEta: false },
  [BankSetupStatus.NEEDS_DATA]: { variant: 'pending', icon: '⏳', action: 'mollie', hasEta: false },
  [BankSetupStatus.IN_REVIEW]: { variant: 'pending', icon: '⏳', action: null, hasEta: true },
  [BankSetupStatus.COMPLETED]: { variant: 'done', icon: '✓', action: null, hasEta: false },
  [BankSetupStatus.BROKEN]: { variant: 'rejected', icon: '!', action: 'bank', hasEta: false },
};

interface AccountCompletionCardProps {
  verificationStatus: VerificationStatus;
  bankStatus: BankSetupStatus;
  /** Back-office reason shown on the KYC row when the dossier was rejected. */
  rejectionReason: string | null;
  /** Mollie hosted-onboarding deep link, used by the NEEDS_DATA call-to-action. */
  mollieDashboardUrl: string | null;
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
  mollieDashboardUrl,
}: AccountCompletionCardProps) {
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
  const bankRow = BANK_ROWS[bankStatus];
  const verified = verificationStatus === VerificationStatus.VERIFIED;
  const bankDone = bankStatus === BankSetupStatus.COMPLETED;
  const isPartial = (verified ? 1 : 0) + (bankDone ? 1 : 0) >= 1;

  const subKey = verificationStatus === VerificationStatus.REJECTED
    ? 'rejected'
    : verified && bankDone
      ? 'allDone'
      : !verified
        ? 'notVerified'
        : 'bankOnly';

  function handleCta(action: CheckAction) {
    if (action === null) return;
    if (action === 'mollie' && mollieDashboardUrl !== null) {
      window.open(mollieDashboardUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    const tab = action === 'verif' ? 'verif' : 'bank';
    router.push(`/${locale}${ROUTES.ASSOCIATION_PROFILE}?tab=${tab}`);
  }

  function handleDismiss() {
    if (userId !== null) localStorage.setItem(dismissedKey(userId, verificationStatus, bankStatus), '1');
    setDismissed(true);
  }

  const checks: { key: 'kyc' | 'bank'; status: string; spec: RowSpec; emoji: string; reason: string | null }[] = [
    { key: 'kyc', status: verificationStatus, spec: kycRow, emoji: '📋', reason: rejectionReason },
    { key: 'bank', status: bankStatus, spec: bankRow, emoji: '🏦', reason: null },
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
        {checks.map(({ key, status, spec, emoji, reason }) => {
          const base = `checks.${key}.${status}`;
          const showReason = spec.variant === 'rejected' && reason !== null && reason !== '';
          return (
            <div key={key} className={`acc-check ${spec.variant}`}>
              <div className="acc-check-ic">{spec.icon}</div>
              <div className="acc-check-body">
                <div className="acc-check-title">
                  <span aria-hidden="true">{emoji}</span> <span>{t(`checks.${key}.title`)}</span>
                </div>
                <div className="acc-check-desc">{t(`${base}.desc`)}</div>
                {showReason && <div className="acc-check-reason">{reason}</div>}
              </div>
              {spec.variant === 'done' && (
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
