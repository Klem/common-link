'use client';

import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { SUGGESTED_AMOUNTS } from '@/lib/donation/donationSchema';
import { useGuestDonation, type DonationTrackingContext } from '@/lib/donation/useGuestDonation';

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
  /** Campaign/association context for the `begin_checkout` dataLayer push made on submit. */
  tracking: DonationTrackingContext;
  skin?: Skin;
  submitLabel?: (amount: number | undefined) => string | undefined;
  /**
   * Renders the whole form inert. Used by the landing page preview when the destination campaign is
   * not published: the donation endpoint would refuse the payment, and an association clicking a live
   * button only to hit an error would conclude its page is broken.
   */
  disabled?: boolean;
  /**
   * Amount the campaign may still accept, from `PublicWidgetDto.remainingCapacity`. When provided,
   * the form refuses a larger amount up front instead of letting the donor fill everything in only
   * to be rejected on submit; `0` means the campaign is full and the form is rendered inert.
   *
   * Undefined leaves the cap entirely to the backend — which checks it in every case (rule 8).
   */
  remainingCapacity?: number;
  onAmountChange?: (amount: number | undefined) => void;
}

export function DonationForm({
  widgetToken,
  sourceSite,
  locale,
  tracking,
  skin = 'default',
  submitLabel,
  disabled = false,
  remainingCapacity,
  onAmountChange,
}: DonationFormProps) {
  const t = useTranslations('widget');
  const s = SKINS[skin];

  const { register, onSubmit, setValue, watch, errors, isSubmitting, submitError, blocked } =
    useGuestDonation({ widgetToken, sourceSite, locale, tracking });

  // A full campaign cannot take any amount, so there is nothing to fill in. Never in preview
  // (`disabled`): an unpublished campaign has no meaningful capacity — its goal may still be zero —
  // and telling an association its own draft page is "already full" is the very confusion the
  // `disabled` state exists to avoid.
  const campaignFull = !disabled && remainingCapacity === 0;

  // `blocked` comes from the submission lifecycle; `disabled` is the caller's decision. Both make
  // every control inert, so the rest of the form reads a single flag.
  const inert = blocked || disabled || campaignFull;

  // At least one preset is disabled by the cap — explains the greyed-out buttons below without
  // disclosing the exact remaining amount.
  const nearingCap =
    !disabled &&
    !campaignFull &&
    remainingCapacity !== undefined &&
    remainingCapacity < Math.max(...SUGGESTED_AMOUNTS);

  const amountValue = watch('amount');

  /**
   * Amount above what the campaign may still collect. Blocks the submit locally; the backend
   * refuses it too (`COLLECTION_CAP_EXCEEDED`) — this only spares the donor a wasted round trip.
   */
  const capExceeded =
    remainingCapacity !== undefined &&
    !campaignFull &&
    Number.isFinite(amountValue) &&
    amountValue > remainingCapacity;

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
        {campaignFull && <p className={s.error}>{t('errors.capFull')}</p>}
        {nearingCap && <p style={styles.hint}>{t('amounts.nearingCap')}</p>}
        <div style={styles.amountGrid}>
          {SUGGESTED_AMOUNTS.map((preset) => (
            <button
              key={preset}
              type="button"
              className={amountValue === preset ? s.amountBtnActive : s.amountBtn}
              onClick={() => handleAmountSelect(preset)}
              disabled={inert || (remainingCapacity !== undefined && preset > remainingCapacity)}
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
            max={remainingCapacity !== undefined ? Math.min(10000, remainingCapacity) : 10000}
            className={s.input}
            placeholder={t('amounts.customPlaceholder')}
            disabled={inert}
            {...register('amount', {
              valueAsNumber: true,
              onChange: (e) => onAmountChange?.(e.target.valueAsNumber || undefined),
            })}
          />
          {capExceeded && (
            <p className={s.error}>{t('errors.capExceeded')}</p>
          )}
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
            disabled={inert}
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
            disabled={inert}
            {...register('donorFullName')}
          />
          {errors.donorFullName && (
            <p className={s.error}>
              {t(errors.donorFullName.message as Parameters<typeof t>[0])}
            </p>
          )}
        </div>

        <div className={s.field}>
          <label htmlFor="donorBirthDate" className={s.label}>
            {t('identity.birthDate')}
          </label>
          <input
            id="donorBirthDate"
            type="date"
            className={s.input}
            disabled={inert}
            {...register('donorBirthDate')}
          />
          <p style={styles.hint}>{t('identity.birthDateHint')}</p>
          {errors.donorBirthDate && (
            <p className={s.error}>
              {t(errors.donorBirthDate.message as Parameters<typeof t>[0])}
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
            disabled={inert}
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
            disabled={inert}
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
              disabled={inert}
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
              disabled={inert}
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
            disabled={inert}
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
            disabled={inert}
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
            disabled={inert}
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

        <div style={{ ...styles.checkRow, marginTop: 12 }}>
          <input
            id="cguAccepted"
            type="checkbox"
            disabled={inert}
            {...register('cguAccepted')}
          />
          <label htmlFor="cguAccepted" style={styles.checkLabel}>
            {t('cgu.label')} <Link href={`/${locale}/legal/CGU`} target="_blank" rel="noopener noreferrer">{t('cgu.link')}</Link> *
          </label>
        </div>
        {errors.cguAccepted && (
          <p className={s.error} style={{ marginTop: 4 }}>
            {t(errors.cguAccepted.message as Parameters<typeof t>[0])}
          </p>
        )}

        <div style={{ ...styles.checkRow, marginTop: 12 }}>
          <input
            id="cgvAccepted"
            type="checkbox"
            disabled={inert}
            {...register('cgvAccepted')}
          />
          <label htmlFor="cgvAccepted" style={styles.checkLabel}>
            {t('cgv.label')} <Link href={`/${locale}/legal/CGV`} target="_blank" rel="noopener noreferrer">{t('cgv.link')}</Link> *
          </label>
        </div>
        {errors.cgvAccepted && (
          <p className={s.error} style={{ marginTop: 4 }}>
            {t(errors.cgvAccepted.message as Parameters<typeof t>[0])}
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
        disabled={isSubmitting || inert || capExceeded}
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
