'use client';

import { useTranslations } from 'next-intl';
import { SUGGESTED_AMOUNTS } from '@/lib/donation/donationSchema';
import { useGuestDonation } from '@/lib/donation/useGuestDonation';

type Skin = 'default' | 'landing';

const SKINS: Record<Skin, {
  amountBtn: string;
  amountBtnActive: string;
  field: string;
  label: string;
  input: string;
  submit: string;
  error: string;
}> = {
  default: {
    amountBtn: 'btn btn-md btn-outline',
    amountBtnActive: 'btn btn-md btn-primary',
    field: 'form-group',
    label: 'form-label',
    input: 'form-input',
    submit: 'btn btn-primary btn-md w-full',
    error: 'form-error',
  },
  landing: {
    amountBtn: 'lp-amount-btn',
    amountBtnActive: 'lp-amount-btn lp-amount-btn--active',
    field: 'lp-field',
    label: 'lp-field-label',
    input: 'lp-field-input',
    submit: 'lp-submit-btn',
    error: 'form-error',
  },
};

interface DonationFormProps {
  widgetToken: string;
  sourceSite: string | null;
  locale: string;
  skin?: Skin;
  submitLabel?: (amount: number | undefined) => string | undefined;
  onAmountChange?: (amount: number | undefined) => void;
}

export function DonationForm({
  widgetToken,
  sourceSite,
  locale,
  skin = 'default',
  submitLabel,
  onAmountChange,
}: DonationFormProps) {
  const t = useTranslations('widget');
  const s = SKINS[skin];

  const { register, onSubmit, setValue, watch, errors, isSubmitting, submitError, blocked } =
    useGuestDonation({ widgetToken, sourceSite, locale });

  const amountValue = watch('amount');

  function handleAmountSelect(preset: number) {
    setValue('amount', preset, { shouldValidate: true });
    onAmountChange?.(preset);
  }

  const resolvedSubmitLabel = submitLabel?.(amountValue);

  return (
    <form onSubmit={onSubmit} noValidate>
      {/* Amount selector */}
      <div style={styles.section}>
        <p style={styles.sectionLabel}>{t('amounts.title')}</p>
        <div style={styles.amountGrid}>
          {SUGGESTED_AMOUNTS.map((preset) => (
            <button
              key={preset}
              type="button"
              className={amountValue === preset ? s.amountBtnActive : s.amountBtn}
              onClick={() => handleAmountSelect(preset)}
              disabled={blocked}
            >
              {preset} €
            </button>
          ))}
        </div>
        <div className={s.field} style={{ marginTop: 8 }}>
          <label htmlFor="custom-amount" className={s.label}>
            {t('amounts.custom')}
          </label>
          <input
            id="custom-amount"
            type="number"
            step="0.01"
            min="1"
            max="10000"
            className={s.input}
            placeholder={t('amounts.customPlaceholder')}
            disabled={blocked}
            {...register('amount', {
              valueAsNumber: true,
              onChange: (e) => onAmountChange?.(e.target.valueAsNumber || undefined),
            })}
          />
          {errors.amount && (
            <p className={s.error}>
              {t(errors.amount.message as Parameters<typeof t>[0])}
            </p>
          )}
        </div>
      </div>

      {/* Identity for receipt */}
      <div style={styles.section}>
        <p style={styles.sectionLabel}>{t('identity.title')}</p>
        <p style={styles.hint}>{t('identity.hint')}</p>

        <div className={s.field}>
          <label htmlFor="donorEmail" className={s.label}>
            {t('identity.email')} *
          </label>
          <input
            id="donorEmail"
            type="email"
            autoComplete="email"
            placeholder={t('identity.emailPlaceholder')}
            className={s.input}
            disabled={blocked}
            {...register('donorEmail')}
          />
          {errors.donorEmail && (
            <p className={s.error}>
              {t(errors.donorEmail.message as Parameters<typeof t>[0])}
            </p>
          )}
        </div>

        <div className={s.field}>
          <label htmlFor="donorFullName" className={s.label}>
            {t('identity.fullName')} *
          </label>
          <input
            id="donorFullName"
            type="text"
            autoComplete="name"
            placeholder={t('identity.fullNamePlaceholder')}
            className={s.input}
            disabled={blocked}
            {...register('donorFullName')}
          />
          {errors.donorFullName && (
            <p className={s.error}>
              {t(errors.donorFullName.message as Parameters<typeof t>[0])}
            </p>
          )}
        </div>

        <div className={s.field}>
          <label htmlFor="donorAddressLine1" className={s.label}>
            {t('identity.addressLine1')} *
          </label>
          <input
            id="donorAddressLine1"
            type="text"
            autoComplete="address-line1"
            placeholder={t('identity.addressLine1Placeholder')}
            className={s.input}
            disabled={blocked}
            {...register('donorAddressLine1')}
          />
          {errors.donorAddressLine1 && (
            <p className={s.error}>
              {t(errors.donorAddressLine1.message as Parameters<typeof t>[0])}
            </p>
          )}
        </div>

        <div className={s.field}>
          <label htmlFor="donorAddressLine2" className={s.label}>
            {t('identity.addressLine2')}
          </label>
          <input
            id="donorAddressLine2"
            type="text"
            autoComplete="address-line2"
            placeholder={t('identity.addressLine2Placeholder')}
            className={s.input}
            disabled={blocked}
            {...register('donorAddressLine2')}
          />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <div className={s.field}>
            <label htmlFor="donorPostalCode" className={s.label}>
              {t('identity.postalCode')} *
            </label>
            <input
              id="donorPostalCode"
              type="text"
              autoComplete="postal-code"
              className={s.input}
              disabled={blocked}
              {...register('donorPostalCode')}
            />
            {errors.donorPostalCode && (
              <p className={s.error}>
                {t(errors.donorPostalCode.message as Parameters<typeof t>[0])}
              </p>
            )}
          </div>
          <div className={s.field}>
            <label htmlFor="donorCity" className={s.label}>
              {t('identity.city')} *
            </label>
            <input
              id="donorCity"
              type="text"
              autoComplete="address-level2"
              className={s.input}
              disabled={blocked}
              {...register('donorCity')}
            />
            {errors.donorCity && (
              <p className={s.error}>
                {t(errors.donorCity.message as Parameters<typeof t>[0])}
              </p>
            )}
          </div>
        </div>

        <div className={s.field}>
          <label htmlFor="donorCountry" className={s.label}>
            {t('identity.country')} *
          </label>
          <input
            id="donorCountry"
            type="text"
            autoComplete="country"
            maxLength={2}
            className={s.input}
            style={{ textTransform: 'uppercase', maxWidth: 80 }}
            disabled={blocked}
            {...register('donorCountry')}
          />
          {errors.donorCountry && (
            <p className={s.error}>
              {t(errors.donorCountry.message as Parameters<typeof t>[0])}
            </p>
          )}
        </div>
      </div>

      {/* Anonymous + consent */}
      <div style={styles.section}>
        <div style={styles.checkRow}>
          <input
            id="anonymousDisplay"
            type="checkbox"
            disabled={blocked}
            {...register('anonymousDisplay')}
          />
          <div>
            <label htmlFor="anonymousDisplay" style={styles.checkLabel}>
              {t('anonymous.label')}
            </label>
            <p style={styles.hint}>{t('anonymous.hint')}</p>
          </div>
        </div>

        <div style={{ ...styles.checkRow, marginTop: 12 }}>
          <input
            id="consent"
            type="checkbox"
            disabled={blocked}
            {...register('consent')}
          />
          <label htmlFor="consent" style={styles.checkLabel}>
            {t('consent.label')} *
          </label>
        </div>
        {errors.consent && (
          <p className={s.error} style={{ marginTop: 4 }}>
            {t(errors.consent.message as Parameters<typeof t>[0])}
          </p>
        )}
      </div>

      {submitError && (
        <p className={s.error} style={{ marginBottom: 12 }}>
          {submitError}
        </p>
      )}

      <button
        type="submit"
        disabled={isSubmitting || blocked}
        className={s.submit}
      >
        {isSubmitting
          ? t('submitting')
          : (resolvedSubmitLabel ?? t('submit'))}
      </button>
    </form>
  );
}

const styles = {
  section: {
    marginBottom: 20,
  },
  sectionLabel: {
    fontWeight: 600,
    fontSize: 15,
    marginBottom: 10,
  },
  hint: {
    fontSize: 12,
    color: 'var(--color-text-2)',
    marginBottom: 8,
  },
  amountGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(4, 1fr)',
    gap: 8,
    marginBottom: 8,
  },
  checkRow: {
    display: 'flex',
    gap: 10,
    alignItems: 'flex-start',
  },
  checkLabel: {
    fontSize: 14,
    cursor: 'pointer',
  },
} as const;
