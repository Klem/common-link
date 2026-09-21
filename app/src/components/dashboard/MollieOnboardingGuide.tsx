'use client';

import { useTranslations } from 'next-intl';
import { useCopyToClipboard } from '@/hooks/ui/useCopyToClipboard';

/** 9 digits — same shape the backend checks before sending a `registrationNumber` to Mollie. */
const SIREN_PATTERN = /^\d{9}$/;

interface MollieOnboardingGuideProps {
  /** Official name of the association, sent to Mollie as `organizationName`. */
  name?: string | null;
  /** Street address of the registered office, sent to Mollie as `address.streetAndNumber`. */
  addressLine1?: string | null;
  postalCode?: string | null;
  city?: string | null;
  /** SIREN, the only identifier the backend forwards as `registrationNumber`. */
  siren?: string | null;
  /** RNA (`W…`) or legacy SIREN — shown to the association, never sent to Mollie as a SIREN. */
  identifier?: string | null;
  /** Objet social, the wording that answers Mollie's "business activity" screen. */
  legalObject?: string | null;
  /** Contact person, sent to Mollie as the account owner. */
  contactName?: string | null;
  contactEmail?: string | null;
}

interface CopyValueProps {
  label: string;
  value: string;
  copyLabel: string;
  copiedLabel: string;
}

/** One stored value the association can paste straight into the Mollie form. */
function CopyValue({ label, value, copyLabel, copiedLabel }: CopyValueProps) {
  const { copied, copy } = useCopyToClipboard();

  return (
    <div className="mollie-guide-value">
      <span className="mollie-guide-value-label">{label}</span>
      <span className="mollie-guide-value-text">{value}</span>
      <button
        type="button"
        className="mollie-guide-copy"
        title={copied ? copiedLabel : copyLabel}
        aria-label={copied ? copiedLabel : copyLabel}
        onClick={() => copy(value)}
      >
        {copied ? copiedLabel : copyLabel}
      </button>
    </div>
  );
}

/**
 * Step-by-step companion for Mollie's hosted onboarding wizard.
 *
 * The wizard runs on Mollie's side, is not configurable, and speaks company vocabulary. Its API
 * gives us nothing to guide the association with: `GET /v2/capabilities` returns coarse
 * requirement ids (`needs-data`, `process-first-payment`), never the list of fields still to fill.
 * So this guide is deliberately static — it restates each screen in association terms and hands
 * back the data CommonLink already holds, ready to paste.
 *
 * It complements [MollieDocumentChecklist]: the checklist answers "which papers do I take out of
 * the drawer", this guide answers "what do I type in this field".
 *
 * Every step is shown to every association. An earlier revision hid the stakeholder and activity
 * steps when no SIREN was recorded; that condition was inferred from two screenshots rather than
 * from any Mollie API, and hiding a step the wizard then asks for is the failure mode that hurts.
 *
 * ⚠ The `review` step quotes the three badge labels of the bank tab (`settings.mollie.status.*`).
 *   Renaming one of those badges without updating the guide would make it describe a status the
 *   association never sees.
 *
 * The copy buttons expose values the association itself provided to CommonLink and that are, for
 * most of them, already pre-filled in the wizard through the client link. No data flows back from
 * Mollie: the direction of the exchange stays CommonLink → Mollie, as audited in
 * `docs/legal/E3-non-delegation-diligences-prestataire.md` §4.2.
 */
export default function MollieOnboardingGuide({
  name,
  addressLine1,
  postalCode,
  city,
  siren,
  identifier,
  legalObject,
  contactName,
  contactEmail,
}: MollieOnboardingGuideProps) {
  const t = useTranslations('settings.mollie.guide');

  const copyLabel = t('copy');
  const copiedLabel = t('copied');

  const trimmedSiren = siren?.trim() || null;
  const trimmedIdentifier = identifier?.trim() || null;
  // The registry number Mollie can actually validate is the SIREN; an RNA is displayed as such so
  // the association does not paste it into a field labelled "commercial register number".
  const registrationValue = trimmedSiren ?? trimmedIdentifier;
  const registrationLabel =
    trimmedSiren || (trimmedIdentifier && SIREN_PATTERN.test(trimmedIdentifier))
      ? t('values.siren')
      : t('values.rna');

  const addressLine = [addressLine1?.trim(), [postalCode?.trim(), city?.trim()].filter(Boolean).join(' ')]
    .filter((part) => part)
    .join(', ');

  /** Keeps a value out of the step when we hold nothing for it, rather than showing an empty row. */
  const known = (label: string, raw?: string | null): { label: string; value: string }[] =>
    raw?.trim() ? [{ label, value: raw.trim() }] : [];

  const steps = [
    {
      id: 'account',
      prefilled: true,
      values: [
        ...known(t('values.contactName'), contactName),
        ...known(t('values.contactEmail'), contactEmail),
      ],
    },
    { id: 'legalForm', prefilled: true, values: [] },
    {
      id: 'organization',
      prefilled: true,
      values: [...known(t('values.name'), name), ...known(t('values.address'), addressLine)],
    },
    {
      id: 'registration',
      prefilled: Boolean(trimmedSiren),
      values: known(registrationLabel, registrationValue),
    },
    { id: 'activity', prefilled: false, values: known(t('values.activity'), legalObject) },
    { id: 'stakeholders', prefilled: false, values: [] },
    { id: 'identity', prefilled: false, values: [] },
    { id: 'bank', prefilled: false, values: [] },
    { id: 'review', prefilled: false, values: [] },
  ];

  return (
    <div className="mollie-guide">
      <p className="mollie-guide-title">{t('title')}</p>
      <p className="mollie-guide-intro">{t('intro')}</p>
      <ol className="mollie-guide-steps">
        {steps.map((step, index) => (
          <li key={step.id} className="mollie-guide-step">
            <details>
              <summary className="mollie-guide-summary">
                <span className="mollie-guide-index">{index + 1}</span>
                <span className="mollie-guide-label">{t(`steps.${step.id}.mollieLabel`)}</span>
                {step.prefilled && <span className="mollie-guide-badge">{t('prefilled')}</span>}
              </summary>
              <div className="mollie-guide-body">
                <p className="mollie-guide-meaning">{t(`steps.${step.id}.meaning`)}</p>
                {step.values.map((value) => (
                  <CopyValue
                    key={value.label}
                    label={value.label}
                    value={value.value}
                    copyLabel={copyLabel}
                    copiedLabel={copiedLabel}
                  />
                ))}
              </div>
            </details>
          </li>
        ))}
      </ol>
      <p className="mollie-guide-note">{t('note')}</p>
    </div>
  );
}
