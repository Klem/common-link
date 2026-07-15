'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { getWidget, createGuestDonation } from '@/lib/api/public';
import type { PublicWidgetDto } from '@/lib/api/public';

const SUGGESTED_AMOUNTS = [10, 25, 50, 100];

const donationSchema = z.object({
  amount: z
    .number({ invalid_type_error: 'errors.amountRequired' })
    .min(1, 'errors.amountMin')
    .max(10000, 'errors.amountMax')
    .refine((v) => Number(v.toFixed(2)) === v, 'errors.amountDecimals'),
  donorEmail: z.string().min(1, 'errors.emailRequired').email('errors.emailInvalid'),
  donorFullName: z.string().min(1, 'errors.fieldRequired').max(255, 'errors.fieldTooLong'),
  donorAddressLine1: z.string().min(1, 'errors.fieldRequired').max(255, 'errors.fieldTooLong'),
  donorAddressLine2: z
    .string()
    .max(255, 'errors.fieldTooLong')
    .optional()
    .transform((v) => (v === '' ? undefined : v)),
  donorPostalCode: z.string().min(1, 'errors.fieldRequired').max(16, 'errors.fieldTooLong'),
  donorCity: z.string().min(1, 'errors.fieldRequired').max(128, 'errors.fieldTooLong'),
  donorCountry: z.string().regex(/^[A-Za-z]{2}$/, 'errors.countryInvalid'),
  anonymousDisplay: z.boolean(),
  consent: z.boolean().refine((v) => v === true, { message: 'errors.consentRequired' }),
});

type DonationFormData = z.infer<typeof donationSchema>;

interface Props {
  widgetToken: string;
  sourceSite: string | null;
}

export function EmbedDonateClient({ widgetToken, sourceSite }: Props) {
  const t = useTranslations('widget');

  const [widget, setWidget] = useState<PublicWidgetDto | null>(null);
  const [widgetLoading, setWidgetLoading] = useState(true);
  const [widgetError, setWidgetError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<DonationFormData>({
    resolver: zodResolver(donationSchema),
    defaultValues: {
      donorCountry: 'FR',
      anonymousDisplay: false,
    },
  });

  const amountValue = watch('amount');

  useEffect(() => {
    getWidget(widgetToken)
      .then(setWidget)
      .catch((err: unknown) => {
        const status =
          err && typeof err === 'object' && 'response' in err
            ? (err as { response?: { status?: number } }).response?.status
            : undefined;
        setWidgetError(status === 404 ? t('unavailable') : t('errors.submitFailed'));
      })
      .finally(() => setWidgetLoading(false));
  }, [widgetToken, t]);

  const onSubmit = handleSubmit(async (data) => {
    setSubmitError(null);
    try {
      const response = await createGuestDonation(widgetToken, {
        ...data,
        sourceSite,
      });
      const top = typeof window !== 'undefined' ? window.top : null;
      if (top) {
        top.location.href = response.checkoutUrl;
      } else {
        window.location.href = response.checkoutUrl;
      }
    } catch {
      setSubmitError(t('errors.submitFailed'));
    }
  });

  if (widgetLoading) {
    return (
      <div style={styles.container}>
        <p style={styles.muted}>{t('loading')}</p>
      </div>
    );
  }

  if (widgetError || !widget) {
    return (
      <div style={styles.container}>
        <p style={styles.error}>{widgetError ?? t('unavailable')}</p>
      </div>
    );
  }

  const progressPct = widget.goal > 0 ? Math.min((widget.raised / widget.goal) * 100, 100) : 0;

  return (
    <div style={styles.container}>
      {/* Campaign header */}
      {widget.campaignCoverImage && (
        <img
          src={widget.campaignCoverImage}
          alt={widget.campaignName}
          style={styles.cover}
        />
      )}
      <div style={styles.campaignHeader}>
        <p style={styles.assocName}>{widget.associationName}</p>
        <h1 style={styles.campaignTitle}>
          {widget.campaignEmoji} {widget.campaignName}
        </h1>
        {widget.campaignDescription && (
          <p style={styles.campaignDesc}>{widget.campaignDescription}</p>
        )}
        <div style={styles.progressBar}>
          <div style={{ ...styles.progressFill, width: `${progressPct}%` }} />
        </div>
        <p style={styles.progressLabel}>
          <strong>{widget.raised.toLocaleString()} {widget.currency}</strong>{' '}
          {t('campaign.raised')} · {t('campaign.goal')} {widget.goal.toLocaleString()} {widget.currency}
        </p>
      </div>

      {/* Donation form */}
      <form onSubmit={onSubmit} noValidate>
        {/* Amount selector */}
        <div style={styles.section}>
          <p style={styles.sectionLabel}>{t('amounts.title')}</p>
          <div style={styles.amountGrid}>
            {SUGGESTED_AMOUNTS.map((preset) => (
              <button
                key={preset}
                type="button"
                className={`btn btn-md ${amountValue === preset ? 'btn-primary' : 'btn-outline'}`}
                onClick={() => setValue('amount', preset, { shouldValidate: true })}
              >
                {preset} €
              </button>
            ))}
          </div>
          <div className="form-group" style={{ marginTop: 8 }}>
            <label htmlFor="custom-amount" className="form-label">
              {t('amounts.custom')}
            </label>
            <input
              id="custom-amount"
              type="number"
              step="0.01"
              min="1"
              max="10000"
              className="form-input"
              placeholder={t('amounts.customPlaceholder')}
              {...register('amount', { valueAsNumber: true })}
            />
            {errors.amount && (
              <p className="form-error">
                {t(errors.amount.message as Parameters<typeof t>[0])}
              </p>
            )}
          </div>
        </div>

        {/* Identity for receipt */}
        <div style={styles.section}>
          <p style={styles.sectionLabel}>{t('identity.title')}</p>
          <p style={styles.hint}>{t('identity.hint')}</p>

          <div className="form-group">
            <label htmlFor="donorEmail" className="form-label">
              {t('identity.email')} *
            </label>
            <input
              id="donorEmail"
              type="email"
              autoComplete="email"
              placeholder={t('identity.emailPlaceholder')}
              className="form-input"
              {...register('donorEmail')}
            />
            {errors.donorEmail && (
              <p className="form-error">
                {t(errors.donorEmail.message as Parameters<typeof t>[0])}
              </p>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="donorFullName" className="form-label">
              {t('identity.fullName')} *
            </label>
            <input
              id="donorFullName"
              type="text"
              autoComplete="name"
              placeholder={t('identity.fullNamePlaceholder')}
              className="form-input"
              {...register('donorFullName')}
            />
            {errors.donorFullName && (
              <p className="form-error">
                {t(errors.donorFullName.message as Parameters<typeof t>[0])}
              </p>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="donorAddressLine1" className="form-label">
              {t('identity.addressLine1')} *
            </label>
            <input
              id="donorAddressLine1"
              type="text"
              autoComplete="address-line1"
              placeholder={t('identity.addressLine1Placeholder')}
              className="form-input"
              {...register('donorAddressLine1')}
            />
            {errors.donorAddressLine1 && (
              <p className="form-error">
                {t(errors.donorAddressLine1.message as Parameters<typeof t>[0])}
              </p>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="donorAddressLine2" className="form-label">
              {t('identity.addressLine2')}
            </label>
            <input
              id="donorAddressLine2"
              type="text"
              autoComplete="address-line2"
              placeholder={t('identity.addressLine2Placeholder')}
              className="form-input"
              {...register('donorAddressLine2')}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="form-group">
              <label htmlFor="donorPostalCode" className="form-label">
                {t('identity.postalCode')} *
              </label>
              <input
                id="donorPostalCode"
                type="text"
                autoComplete="postal-code"
                className="form-input"
                {...register('donorPostalCode')}
              />
              {errors.donorPostalCode && (
                <p className="form-error">
                  {t(errors.donorPostalCode.message as Parameters<typeof t>[0])}
                </p>
              )}
            </div>
            <div className="form-group">
              <label htmlFor="donorCity" className="form-label">
                {t('identity.city')} *
              </label>
              <input
                id="donorCity"
                type="text"
                autoComplete="address-level2"
                className="form-input"
                {...register('donorCity')}
              />
              {errors.donorCity && (
                <p className="form-error">
                  {t(errors.donorCity.message as Parameters<typeof t>[0])}
                </p>
              )}
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="donorCountry" className="form-label">
              {t('identity.country')} *
            </label>
            <input
              id="donorCountry"
              type="text"
              autoComplete="country"
              maxLength={2}
              className="form-input"
              style={{ textTransform: 'uppercase', maxWidth: 80 }}
              {...register('donorCountry')}
            />
            {errors.donorCountry && (
              <p className="form-error">
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
              {...register('consent')}
            />
            <label htmlFor="consent" style={styles.checkLabel}>
              {t('consent.label')} *
            </label>
          </div>
          {errors.consent && (
            <p className="form-error" style={{ marginTop: 4 }}>
              {t(errors.consent.message as Parameters<typeof t>[0])}
            </p>
          )}
        </div>

        {submitError && <p className="form-error" style={{ marginBottom: 12 }}>{submitError}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="btn btn-primary btn-md w-full"
        >
          {isSubmitting ? t('submitting') : t('submit')}
        </button>
      </form>
    </div>
  );
}

const styles = {
  container: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '16px',
    fontFamily: 'var(--font-body)',
    color: 'var(--color-text)',
  },
  cover: {
    width: '100%',
    height: 160,
    objectFit: 'cover' as const,
    borderRadius: 8,
    marginBottom: 12,
  },
  campaignHeader: {
    marginBottom: 20,
  },
  assocName: {
    fontSize: 12,
    color: 'var(--color-text-2)',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.05em',
    marginBottom: 4,
  },
  campaignTitle: {
    fontSize: 20,
    fontWeight: 700,
    marginBottom: 8,
  },
  campaignDesc: {
    fontSize: 14,
    color: 'var(--color-text-2)',
    marginBottom: 12,
  },
  progressBar: {
    height: 6,
    background: 'var(--color-bg-2)',
    borderRadius: 3,
    overflow: 'hidden' as const,
    marginBottom: 6,
  },
  progressFill: {
    height: '100%',
    background: 'var(--bright-teal)',
    borderRadius: 3,
    transition: 'width 0.3s',
  },
  progressLabel: {
    fontSize: 13,
    color: 'var(--color-text-2)',
  },
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
  muted: {
    color: 'var(--color-text-2)',
    fontSize: 14,
  },
  error: {
    color: 'var(--color-error)',
    fontSize: 14,
  },
} as const;
