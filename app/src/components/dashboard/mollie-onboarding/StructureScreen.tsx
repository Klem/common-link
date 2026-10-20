'use client';

import type { ReactNode } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { MollieStructureVariant } from '@/types/mollie-connect';
import type { MollieOnboardingContext, OnboardingBeneficialOwner } from '@/types/mollie-connect';
import { STRUCTURE_KIT } from './documentKit';

/** Avatar colours, cycled through in list order as in the mockup. */
const AVATAR_COLORS = ['avatar-indigo', 'avatar-teal', 'avatar-coral', 'avatar-amber'] as const;

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

/** Up to two initials from a display name ("Claire Martin" → "CM"). */
function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toLocaleUpperCase('fr'))
    .join('');
}

interface StructureScreenProps {
  /** Onboarding context, null while loading or after a failed load. */
  context: MollieOnboardingContext | null;
  /** Deduplicated people whose identity document Mollie will ask for (see `onboardingPeople`). */
  people: OnboardingBeneficialOwner[];
  onBack: () => void;
  onContinue: () => void;
  /** Rendered instead of the KYB cards when the context could not be loaded. */
  contextErrorSlot?: ReactNode;
}

/**
 * Screen 1 "Votre structure": the structure variant determined server-side from the KYB legal
 * category (no free selector) and the people recorded during the CommonLink verification.
 *
 * `UNKNOWN` shows a sober "not determined" state. The mockup's "Ce n'est pas votre situation ?
 * Contactez-nous" link is hidden until a contact destination is defined (question Q6).
 */
export default function StructureScreen({ context, people, onBack, onContinue, contextErrorSlot }: StructureScreenProps) {
  const t = useTranslations('settings.mollie.onboarding.structure');
  const locale = useLocale();

  const variant = context?.structureVariant ?? MollieStructureVariant.UNKNOWN;
  const known = variant !== MollieStructureVariant.UNKNOWN;
  const verifiedAt = context?.verifiedAt ? new Date(context.verifiedAt) : null;
  const count = people.length;

  return (
    <section className="screen visible" aria-labelledby="mob-s1-title">
      <h2 id="mob-s1-title" className="mob-screen-title">{t('title')}</h2>
      <p className="lead mob-lead-spaced">{t('lead')}</p>

      {contextErrorSlot ?? (
        <>
          <div className="card card-no-hover">
            <div className="card-body">
              <div className="row-between">
                <div className="row">
                  <div className="expense-icon" aria-hidden="true">🏛️</div>
                  <div>
                    <div className="small muted mob-variant-caption">{t('typeCaption')}</div>
                    <div className="mob-variant-type">{t(`types.${STRUCTURE_KIT[variant].key}`)}</div>
                    {verifiedAt && (
                      <div className="small muted">
                        {t('verifiedOn', {
                          date: verifiedAt.toLocaleDateString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
                            day: '2-digit',
                            month: '2-digit',
                            year: 'numeric',
                          }),
                        })}
                      </div>
                    )}
                  </div>
                </div>
                {verifiedAt && <span className="badge badge-success">{t('verifiedBadge')}</span>}
              </div>
              {!known && <p className="small muted mob-variant-contact">{t('unknownHint')}</p>}
              {/* TODO(Q6): "Ce n'est pas votre situation ? Contactez-nous" hidden — no contact destination defined yet. */}
            </div>
          </div>

          <div className="card card-no-hover mob-card-spaced">
            <div className="card-header-bar">
              <h3>{t('owners.title')}</h3>
              <span className="badge badge-info">{t('owners.count', { count })}</span>
            </div>
            <div className="card-body">
              <p className="small muted mob-be-intro">{t('owners.intro')}</p>
              {count === 0 ? (
                <p className="small">{t('owners.empty')}</p>
              ) : (
                <>
                  <div className="kit-list">
                    {people.map((person, index) => (
                      <div key={`${person.name}-${index}`} className="expense-card mob-be-row">
                        <div className={`avatar avatar-md ${AVATAR_COLORS[index % AVATAR_COLORS.length]}`} aria-hidden="true">
                          {initials(person.name)}
                        </div>
                        <div className="expense-info">
                          <h4>{person.name}</h4>
                          {person.role && <span>{person.role}</span>}
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="alert alert-info mob-alert-top">
                    <span className="alert-icon" aria-hidden="true">🪪</span>
                    <div>{t.rich('owners.idAlert', { count, strong })}</div>
                  </div>
                </>
              )}
              <div className="alert alert-warning mob-alert-top-sm">
                <span className="alert-icon" aria-hidden="true">⚠️</span>
                <div>{t.rich('owners.presidencyAlert', { strong })}</div>
              </div>
            </div>
          </div>
        </>
      )}

      <div className="screen-foot">
        <button type="button" className="btn btn-ghost" onClick={onBack}>
          <span aria-hidden="true">←</span> {t('back')}
        </button>
        <button type="button" className="btn btn-primary" onClick={onContinue}>
          {t('continue')}
        </button>
      </div>
    </section>
  );
}
