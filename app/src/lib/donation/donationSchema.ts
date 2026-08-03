import { z } from 'zod';

export const SUGGESTED_AMOUNTS = [10, 25, 50, 100];

export const donationSchema = z.object({
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

export type DonationFormData = z.infer<typeof donationSchema>;
