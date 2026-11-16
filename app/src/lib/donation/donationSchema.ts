import { z } from 'zod';

export const SUGGESTED_AMOUNTS = [10, 25, 50, 100];

export const donationSchema = z.object({
  amount: z
    .number({ invalid_type_error: 'errors.amountRequired' })
    .min(1, 'errors.amountMin')
    .max(10000, 'errors.amountMax')
    .refine((v) => Number(v.toFixed(2)) === v, 'errors.amountDecimals'),
  donorEmail: z.string().min(1, 'errors.emailRequired').email('errors.emailInvalid'),
  donorFullName: z
    .string()
    .min(1, 'errors.fieldRequired')
    .max(255, 'errors.fieldTooLong')
    .regex(/^\s*\S+(\s+\S+)+\s*$/, 'errors.fullNameIncomplete'),
  // Facultative : sert uniquement au filtrage gel côté API, qui ne la conserve pas.
  // Le `transform` est indispensable — un input date vidé renvoie '', que l'API refuserait
  // (400 sur un LocalDate) là où l'absence est acceptée.
  donorBirthDate: z
    .string()
    .optional()
    .transform((v) => (v === '' ? undefined : v)),
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
  // Distinct from `consent` (RGPD only) — art. 1740 A CGI proof of acceptance. The donor accepts the
  // CGU only: the CGV document is the associations' CGU.
  cguAccepted: z.boolean().refine((v) => v === true, { message: 'errors.cguRequired' }),
  // Version of the CGU text loaded by the form, sent back as the accepted version. Empty while the text
  // could not be loaded — mirrors `@NotBlank @Size(max = 32)` on CreateGuestDonationRequest.cguVersion.
  cguVersion: z.string().min(1, 'errors.legalUnavailable').max(32, 'errors.legalUnavailable'),
});

export type DonationFormData = z.infer<typeof donationSchema>;
