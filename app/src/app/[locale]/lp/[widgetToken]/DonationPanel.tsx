'use client';

import { useTranslations } from 'next-intl';
import { DonationForm } from '@/components/donation/DonationForm';
import type { DonationTrackingContext } from '@/lib/donation/useGuestDonation';

interface Props {
  widgetToken: string;
  sourceSite: string | null;
  locale: string;
  tracking: DonationTrackingContext;
  /** Renders every field and the submit button disabled — preview on an unpublished campaign. */
  disabled?: boolean;
  /** Amount the campaign may still accept; forwarded to the form's collection-cap guard. */
  remainingCapacity?: number;
  onAmountChange?: (amount: number | undefined) => void;
  /** Validated host origin when the landing runs inside `landing.js`/an iframe — see `DonationEventExtras.parentOrigin`. */
  parentOrigin?: string | null;
}

export function DonationPanel({
  widgetToken,
  sourceSite,
  locale,
  tracking,
  disabled,
  remainingCapacity,
  onAmountChange,
  parentOrigin,
}: Props) {
  const t = useTranslations('landing');

  return (
    <div id="don" className="lp-donation-panel">
      <span className="lp-eyebrow">{t('donate.eyebrow')}</span>
      <h2 className="lp-donation-panel-title">{t('donate.title')}</h2>
      <DonationForm
        skin="landing"
        widgetToken={widgetToken}
        sourceSite={sourceSite}
        locale={locale}
        tracking={tracking}
        submitLabel={(amount) => (amount ? t('donate.submitWithAmount', { amount }) : undefined)}
        disabled={disabled}
        remainingCapacity={remainingCapacity}
        onAmountChange={onAmountChange}
        parentOrigin={parentOrigin}
      />
      <div className="lp-payment-methods">
        <span className="lp-payment-pill">CB</span>
        <span className="lp-payment-pill">Visa</span>
        <span className="lp-payment-pill">Mastercard</span>
      </div>
      <p className="lp-donation-secured">{t('donate.secured')}</p>
    </div>
  );
}
