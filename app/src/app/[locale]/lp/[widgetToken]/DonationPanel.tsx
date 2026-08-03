'use client';

import { useTranslations } from 'next-intl';
import { DonationForm } from '@/components/donation/DonationForm';

interface Props {
  widgetToken: string;
  sourceSite: string | null;
  locale: string;
  onAmountChange?: (amount: number | undefined) => void;
}

export function DonationPanel({ widgetToken, sourceSite, locale, onAmountChange }: Props) {
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
        submitLabel={(amount) => (amount ? t('donate.submitWithAmount', { amount }) : undefined)}
        onAmountChange={onAmountChange}
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
