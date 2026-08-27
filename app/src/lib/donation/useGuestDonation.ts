'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslations } from 'next-intl';
import { createGuestDonation } from '@/lib/api/public';
import { pushDonationEvent, captureUtmParams } from '@/lib/gtm';
import { donationSchema, type DonationFormData } from './donationSchema';

/** Campaign/association context needed to fill the GA4 ecommerce `items`/`affiliation` fields. */
export interface DonationTrackingContext {
  campaignId: string;
  campaignName: string;
  associationName: string;
  currency: string;
}

interface UseGuestDonationOptions {
  widgetToken: string;
  sourceSite: string | null;
  locale: string;
  tracking: DonationTrackingContext;
}

export function useGuestDonation({ widgetToken, sourceSite, locale, tracking }: UseGuestDonationOptions) {
  const t = useTranslations('widget');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [blocked, setBlocked] = useState(false);

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
      cguAccepted: false,
      cgvAccepted: false,
    },
  });

  const onSubmit = handleSubmit(async (data) => {
    setSubmitError(null);
    setBlocked(false);
    try {
      const response = await createGuestDonation(widgetToken, {
        ...data,
        sourceSite,
        locale,
      });
      pushDonationEvent(
        'begin_checkout',
        {
          transaction_id: response.publicRef,
          value: data.amount,
          currency: tracking.currency,
          items: [{ item_id: tracking.campaignId, item_name: tracking.campaignName }],
          affiliation: tracking.associationName,
        },
        { anonymous: data.anonymousDisplay, utm: captureUtmParams() },
      );
      const top = typeof window !== 'undefined' ? window.top : null;
      if (top) {
        top.location.href = response.checkoutUrl;
      } else {
        window.location.href = response.checkoutUrl;
      }
    } catch (err) {
      const response =
        err && typeof err === 'object' && 'response' in err
          ? (err as { response?: { status?: number; data?: { code?: string } } }).response
          : undefined;
      if (response?.status === 409 && response.data?.code === 'COLLECTION_CAP_EXCEEDED') {
        // Refused on the collection cap, not on the association's ability to collect. The donor can
        // succeed with a lower amount, so the form stays live — `blocked` would make it inert.
        setSubmitError(t('errors.capExceeded'));
      } else if (response?.status === 409) {
        setBlocked(true);
        setSubmitError(t('errors.notCollecting'));
      } else {
        setSubmitError(t('errors.submitFailed'));
      }
    }
  });

  return { register, onSubmit, setValue, watch, errors, isSubmitting, submitError, blocked };
}
