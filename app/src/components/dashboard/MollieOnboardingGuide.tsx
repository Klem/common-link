'use client';

import { useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { useCopyToClipboard } from '@/hooks/ui/useCopyToClipboard';
import type { OnboardingBeneficialOwner } from '@/types/mollie-connect';

/** 9 digits — same shape the backend checks before sending a `registrationNumber` to Mollie. */
const SIREN_PATTERN = /^\d{9}$/;

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface MollieOnboardingGuideProps {
  /** Official name of the association, sent to Mollie as `organizationName`. */
  name?: string | null;
  /** Street address of the registered office, sent to Mollie as `address.streetAndNumber`. */
  addressLine1?: string | null;
  postalCode?: string | null;
  city?: string | null;
  /** SIREN, the only identifier the backend forwards as `registrationNumber`; never an RNA. */
  siren?: string | null;
  /** Objet social, the wording that answers Mollie's "business activity" screen. */
  legalObject?: string | null;
  /** Contact person, sent to Mollie as the account owner. */
  contactName?: string | null;
  contactEmail?: string | null;
  /**
   * People to declare as stakeholders (confirmed, non-discarded KYB beneficial owners and
   * representatives, see `onboardingPeople`). Empty when unknown (e.g. in the connection modal).
   */
  people?: OnboardingBeneficialOwner[];
}

interface CopyRowProps {
  label: string;
  value: string;
}

/** One stored value the association can paste straight into the Mollie form (mockup `copy-row`). */
function CopyRow({ label, value }: CopyRowProps) {
  const t = useTranslations('settings.mollie.guide');
  const { copied, copy } = useCopyToClipboard();

  return (
    <div className="copy-row">
      <span className="copy-row-label">{label}</span>
      <span className="copy-row-value">{value}</span>
      <button
        type="button"
        className={`copy-btn${copied ? ' copied' : ''}`}
        aria-label={t('copyAria', { label })}
        onClick={() => void copy(value)}
      >
        {copied ? t('copied') : t('copy')}
      </button>
    </div>
  );
}

/** Steps of Mollie's hosted wizard, in wizard order (mockup screen 3, 10 steps). */
const STEP_IDS = [
  'account',
  'legalForm',
  'organization',
  'registration',
  'activity',
  'publicInterest',
  'stakeholders',
  'identity',
  'bank',
  'review',
] as const;
type StepId = typeof STEP_IDS[number];

/** Steps the client link pre-fills (mockup: steps 1-3 badged "Déjà pré-rempli"). */
const PREFILLED: ReadonlySet<StepId> = new Set<StepId>(['account', 'legalForm', 'organization']);

/**
 * "Translator" for Mollie's hosted onboarding wizard (mockup screen 3, `tr-steps`): for each of
 * its 10 screens, the label Mollie shows next to what it means for an association, with the data
 * CommonLink already holds ready to copy.
 *
 * The wizard runs on Mollie's side, is not configurable, and speaks company vocabulary; its API
 * gives no list of fields still to fill, so this guide is deliberately static. Every step is shown
 * to every association: hiding a step the wizard then asks for is the failure mode that hurts.
 *
 * Fixed wording: the legal form instruction is decision D14 ("Association", in every variant);
 * the activity step names the "Œuvres caritatives et dons" category and announces opening fees
 * without an amount (D8); the "organisation d'intérêt public" step is badged "to be confirmed with
 * Mollie" and deliberately not interpreted.
 *
 * Used by screen 3 of the bank tab and by `MollieOnboardModal` while the OAuth window is open.
 * Values only go CommonLink → Mollie (pasted by the association itself); nothing comes back from
 * Mollie, as audited in `docs/legal/E3-non-delegation-diligences-prestataire.md` §4.2.
 */
export default function MollieOnboardingGuide({
  name,
  addressLine1,
  postalCode,
  city,
  siren,
  legalObject,
  contactName,
  contactEmail,
  people = [],
}: MollieOnboardingGuideProps) {
  const t = useTranslations('settings.mollie.guide');
  const [open, setOpen] = useState<Partial<Record<StepId, boolean>>>({});
  const [current, setCurrent] = useState<StepId | null>(null);

  const trimmedName = name?.trim() || null;
  const trimmedSiren = siren?.trim() || null;
  const sirenValue = trimmedSiren && SIREN_PATTERN.test(trimmedSiren) ? trimmedSiren : null;
  const addressLine = [addressLine1?.trim(), [postalCode?.trim(), city?.trim()].filter(Boolean).join(' ')]
    .filter((part) => part)
    .join(', ');
  const count = people.length;

  /** Keeps a value out of the step when we hold nothing for it, rather than showing an empty row. */
  const copyRow = (label: string, raw?: string | null) =>
    raw?.trim() ? <CopyRow key={label} label={label} value={raw.trim()} /> : null;

  const meaning: Record<StepId, ReactNode> = {
    account: (
      <>
        <p>{t('steps.account.p1')}</p>
        {copyRow(t('values.contactName'), contactName)}
        {copyRow(t('values.contactEmail'), contactEmail)}
      </>
    ),
    legalForm: (
      <>
        <p>{t.rich('steps.legalForm.p1', { strong })}</p>
        <div className="alert alert-warning">{t('steps.legalForm.warning')}</div>
      </>
    ),
    organization: (
      <>
        <p>{t('steps.organization.p1')}</p>
        {copyRow(t('values.name'), trimmedName)}
        {copyRow(t('values.address'), addressLine)}
      </>
    ),
    registration: (
      <>
        <p>{t.rich('steps.registration.p1', { strong })}</p>
        {copyRow(t('values.siren'), sirenValue)}
      </>
    ),
    activity: (
      <>
        <p>{t.rich('steps.activity.p1', { strong })}</p>
        {copyRow(t('values.activity'), legalObject)}
        <div className="alert alert-warning small mob-tr-alert">
          <span className="alert-icon" aria-hidden="true">💶</span>
          <div>{t.rich('steps.activity.fees', { strong })}</div>
        </div>
      </>
    ),
    publicInterest: <p>{t('steps.publicInterest.p1')}</p>,
    stakeholders: (
      <>
        <p>{t.rich('steps.stakeholders.p1', { count, strong })}</p>
        {people.map((person, index) => (
          <CopyRow key={`${person.name}-${index}`} label={person.role?.trim() || t('values.person')} value={person.name} />
        ))}
      </>
    ),
    identity: (
      <>
        <p>{t.rich('steps.identity.p1', { count, strong })}</p>
        <p>{t.rich('steps.identity.p2', { strong })}</p>
      </>
    ),
    bank: (
      <>
        <p>{trimmedName ? t('steps.bank.p1', { name: trimmedName }) : t('steps.bank.p1WithoutName')}</p>
        <p>{t('steps.bank.p2')}</p>
      </>
    ),
    review: <p>{t('steps.review.p1')}</p>,
  };

  /** Mollie-side hint under the label, for the steps whose wizard screen says more. */
  const mollieHint: Partial<Record<StepId, string>> = {
    publicInterest: t('steps.publicInterest.mollieHint'),
    stakeholders: t('steps.stakeholders.mollieHint'),
  };

  const toggle = (id: StepId) => {
    const expanded = open[id] === true;
    setOpen((prev) => ({ ...prev, [id]: !expanded }));
    setCurrent(expanded ? null : id);
  };

  return (
    <ol className="tr-steps">
      {STEP_IDS.map((id, index) => {
        const expanded = open[id] === true;
        const bodyId = `mob-tr-${id}`;
        const classes = ['tr-step', PREFILLED.has(id) && 'prefilled', current === id && 'current'].filter(Boolean).join(' ');
        return (
          <li key={id} className={classes}>
            <button type="button" className="tr-head" aria-expanded={expanded} aria-controls={bodyId} onClick={() => toggle(id)}>
              <span className="tr-num">{index + 1}</span>
              <span className="tr-title">{t(`steps.${id}.title`)}</span>
              {PREFILLED.has(id) && <span className="badge badge-active">{t('prefilled')}</span>}
              {id === 'publicInterest' && <span className="badge badge-warning">{t('toConfirm')}</span>}
            </button>
            <div className="tr-body" id={bodyId} hidden={!expanded}>
              <div className="tr-col">
                <div className="tr-col-label">{t('colMollie')}</div>
                <div className="tr-mollie-label">{t(`steps.${id}.mollieLabel`)}</div>
                {mollieHint[id] && <p className="small muted mob-tr-hint">{mollieHint[id]}</p>}
              </div>
              <div className="tr-col">
                <div className="tr-col-label">{t('colMeaning')}</div>
                {meaning[id]}
              </div>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
