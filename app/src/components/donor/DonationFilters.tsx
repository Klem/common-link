'use client';

import { useTranslations } from 'next-intl';
import type { AssociationOptionDto } from '@/types/donor';

interface Props {
  associations: AssociationOptionDto[];
  years: number[];
  associationId: string | null;
  year: number | null;
  onAssociationChange: (associationId: string | null) => void;
  onYearChange: (year: number | null) => void;
}

/** Association and year filters for the donation history page. */
export function DonationFilters({
  associations,
  years,
  associationId,
  year,
  onAssociationChange,
  onYearChange,
}: Props) {
  const t = useTranslations('dashboard.donor.donations.filters');

  return (
    <div className="flex flex-col sm:flex-row gap-4">
      <div className="form-group flex-1 mb-0">
        <label htmlFor="donation-filter-association" className="form-label">
          {t('association')}
        </label>
        <select
          id="donation-filter-association"
          className="form-input"
          value={associationId ?? ''}
          onChange={(e) => onAssociationChange(e.target.value || null)}
        >
          <option value="">{t('allAssociations')}</option>
          {associations.map((association) => (
            <option key={association.id} value={association.id}>
              {association.name}
            </option>
          ))}
        </select>
      </div>

      <div className="form-group flex-1 mb-0">
        <label htmlFor="donation-filter-year" className="form-label">
          {t('year')}
        </label>
        <select
          id="donation-filter-year"
          className="form-input"
          value={year ?? ''}
          onChange={(e) => onYearChange(e.target.value ? Number(e.target.value) : null)}
        >
          <option value="">{t('allYears')}</option>
          {years.map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
