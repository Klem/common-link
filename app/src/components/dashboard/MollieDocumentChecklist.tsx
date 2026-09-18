'use client';

import { useTranslations } from 'next-intl';

/**
 * Public search page of the French public-entity directory, which indexes associations too.
 * A bare SIREN or RNA is a valid search term, which avoids depending on the slugged
 * `/entreprise/<slug>-<siren>` permalink we cannot build from the data we store (we hold an
 * identifier, never the directory slug).
 */
const ANNUAIRE_SEARCH_URL = 'https://annuaire-entreprises.data.gouv.fr/rechercher?terme=';

/** 9 digits. */
const SIREN_PATTERN = /^\d{9}$/;
/** Current RNA format: `W` followed by 9 alphanumerics (Corsican départements use `W2A`/`W2B`). */
const RNA_PATTERN = /^W[0-9A-Z]{9}$/i;

interface MollieDocumentChecklistProps {
  /** Association SIREN, preferred search term for the registry lookup. */
  siren?: string | null;
  /**
   * Association primary identifier — an RNA (`W…`) for associations declared without a SIREN,
   * a SIREN for legacy registrations. Used as a fallback search term when [siren] is absent.
   */
  identifier?: string | null;
}

/**
 * Translates Mollie's hosted KYB checklist into terms a loi-1901 association understands.
 *
 * Mollie's onboarding wizard is hosted on their side and is not configurable: it asks for a
 * "Certificat d'immatriculation de votre entreprise" even when the organization was created
 * with `legalEntity = fr-association`. Associations have no such certificate, which makes the
 * flow look like it was not meant for them. This component states, before and during the flow,
 * which association document satisfies each Mollie requirement.
 *
 * Purely informational — it never blocks or gates the connect action.
 *
 * The registration item carries a lookup link when the association can be found in the public
 * directory, by SIREN when there is one, by RNA otherwise.
 */
export default function MollieDocumentChecklist({ siren, identifier }: MollieDocumentChecklistProps) {
  const t = useTranslations('settings.mollie.checklist');

  // The directory indexes associations by RNA as well as by SIREN, so `identifier` also works as a
  // search term — but only in those two formats: anything else would open an empty result page,
  // which reads as a broken feature, so it gets no link at all. SIREN first when both exist: it
  // designates exactly one entity, whereas a head office and its local branches can share a single
  // RNA, which then returns several rows.
  const fallback = identifier?.trim();
  const searchableFallback =
    fallback && (SIREN_PATTERN.test(fallback) || RNA_PATTERN.test(fallback)) ? fallback : null;
  const registryTerm = siren?.trim() || searchableFallback;

  const items = [
    {
      id: 'registration',
      mollieLabel: t('items.registration.mollieLabel'),
      meaning: t('items.registration.meaning'),
    },
    {
      id: 'identity',
      mollieLabel: t('items.identity.mollieLabel'),
      meaning: t('items.identity.meaning'),
    },
    {
      id: 'bank',
      mollieLabel: t('items.bank.mollieLabel'),
      meaning: t('items.bank.meaning'),
    },
  ];

  return (
    <div className="mollie-checklist">
      <p className="mollie-checklist-intro">{t('intro')}</p>
      <ul className="mollie-checklist-list">
        {items.map((item) => (
          <li key={item.id} className="mollie-checklist-item">
            <span className="mollie-checklist-label">{item.mollieLabel}</span>
            <span className="mollie-checklist-meaning">{item.meaning}</span>
            {item.id === 'registration' && registryTerm && (
              <a
                href={`${ANNUAIRE_SEARCH_URL}${encodeURIComponent(registryTerm)}`}
                target="_blank"
                rel="noopener noreferrer"
                className="mollie-checklist-link"
              >
                {t('items.registration.link')}
              </a>
            )}
          </li>
        ))}
      </ul>
      <p className="mollie-checklist-note">{t('note')}</p>
    </div>
  );
}
