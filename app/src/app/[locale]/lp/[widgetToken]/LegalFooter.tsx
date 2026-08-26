'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { ReportCampaignModal } from './ReportCampaignModal';

interface Props {
  widgetToken: string;
  associationName: string;
  addressLine1: string | null;
  postalCode: string | null;
  city: string | null;
  associationRna: string;
  legalObject: string | null;
  taxReductionRate: number;
}

function buildAddressLine(
  addressLine1: string | null,
  postalCode: string | null,
  city: string | null,
  associationRna: string,
): string {
  const parts: string[] = [];
  if (addressLine1) parts.push(addressLine1);
  const cityPart = [postalCode, city].filter(Boolean).join(' ');
  if (cityPart) parts.push(cityPart);
  const address = parts.join(', ');
  return address ? `${address} — RNA : ${associationRna}` : `RNA : ${associationRna}`;
}

export function LegalFooter({
  widgetToken,
  associationName,
  addressLine1,
  postalCode,
  city,
  associationRna,
  legalObject,
  taxReductionRate,
}: Props) {
  const t = useTranslations('landing');
  const [isReportOpen, setIsReportOpen] = useState(false);

  return (
    <footer className="lp-footer">
      <div className="lp-container">
        <p className="lp-footer-name">{associationName}</p>
        <p>{buildAddressLine(addressLine1, postalCode, city, associationRna)}</p>
        {legalObject && (
          <p>
            <strong>{t('footer.object')}</strong> {legalObject}
          </p>
        )}
        <p>{t('footer.taxMention', { rate: taxReductionRate })}</p>
        <p>
          <button
            type="button"
            className="lp-footer-report"
            onClick={() => setIsReportOpen(true)}
          >
            {t('report.trigger')}
          </button>
        </p>
      </div>
      <ReportCampaignModal
        isOpen={isReportOpen}
        widgetToken={widgetToken}
        onClose={() => setIsReportOpen(false)}
      />
    </footer>
  );
}
