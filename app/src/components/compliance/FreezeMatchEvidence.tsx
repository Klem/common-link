'use client';

import { useTranslations } from 'next-intl';
import type { FreezeScreeningMatchDto, SubjectRegistryDto } from '@/types/compliance';

interface FreezeMatchEvidenceProps {
  matches: FreezeScreeningMatchDto[];
  subjectLabel: string | null;
  subjectId: string | null;
  subjectRegistry: SubjectRegistryDto | null;
  locale: string;
}

function formatDate(iso: string | null, locale: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
    dateStyle: 'medium',
  });
}

function formatScore(score: number): string {
  return score.toFixed(4);
}

function formatTriState(
  value: boolean | null,
  t: (key: string) => string,
): string {
  if (value === null) return t('evidence.unknown');
  return value ? t('evidence.yes') : t('evidence.no');
}

interface ScreenedGroup {
  key: string;
  subjectType: string;
  screenedNormalizedName: string;
  matches: FreezeScreeningMatchDto[];
}

/**
 * Groups correspondences by the party that was screened.
 *
 * An onboarding alert is carried by the association but covers several screened parties — the
 * association itself, each registry officer, each manually entered legal representative, each
 * beneficial owner. Their correspondences all resolve to the same alert. Listed in one flat table
 * under a single "value compared" heading, a person-name match would appear to have scored
 * against the association's name. Each screened party therefore gets its own block.
 */
function groupByScreenedParty(matches: FreezeScreeningMatchDto[]): ScreenedGroup[] {
  const groups = new Map<string, ScreenedGroup>();
  for (const match of matches) {
    const key = `${match.subjectType}|${match.subjectId}|${match.screenedNormalizedName}`;
    const existing = groups.get(key);
    if (existing) {
      existing.matches.push(match);
    } else {
      groups.set(key, {
        key,
        subjectType: match.subjectType,
        screenedNormalizedName: match.screenedNormalizedName,
        matches: [match],
      });
    }
  }
  return Array.from(groups.values());
}

/**
 * Side-by-side presentation of what was screened and what it matched in the asset-freeze register.
 *
 * This is the evidence the compliance officer's decision rests on. Before it existed the alert
 * showed only aggregates ("3 correspondances, 0.93"), which is enough to prove a control took
 * place but not enough to rule on it.
 *
 * Three display choices carry the reasoning:
 * - the **normalized** screened value is shown, not the raw dossier name — "TECHNO +" is compared
 *   as "TECHNO", and without that the 0.9333 against "TECHNOLAB" looks arbitrary;
 * - the subject's public-registry identity sits opposite the match, because that is what
 *   discriminates: an active RNA and a verified SIREN against a foreign sanctions programme;
 * - correspondences are grouped by screened party, since one alert covers the association, its
 *   dirigeants and its beneficial owners, and a match must never be read against the wrong name.
 */
export function FreezeMatchEvidence({
  matches,
  subjectLabel,
  subjectId,
  subjectRegistry,
  locale,
}: FreezeMatchEvidenceProps) {
  const t = useTranslations('compliance');

  const groups = groupByScreenedParty(matches);
  // Screening conditions are set per run, so they are identical across every correspondence of an
  // alert — unlike the screened value, which varies by party and belongs to each group.
  const threshold = matches.length > 0 ? matches[0].scoreThreshold : null;
  const algorithm = matches.length > 0 ? matches[0].algorithm : null;
  const registryDate = matches.length > 0 ? matches[0].registryPublicationDate : null;

  return (
    <div className="cm-card" style={{ marginBottom: 24 }}>
      <div className="cm-card-title">{t('evidence.title')}</div>

      {matches.length === 0 ? (
        <p style={{ color: 'var(--color-text-2)', marginTop: 12 }}>{t('evidence.empty')}</p>
      ) : (
        <>
          <div className="budget-cols" style={{ marginTop: 12, gap: 24 }}>
            {/* Left: what was screened */}
            <div>
              <p className="cm-label" style={{ marginBottom: 8 }}>{t('evidence.screenedSide')}</p>

              <div style={{ marginBottom: 10 }}>
                <span className="cm-label">{t('evidence.subject')}</span>
                <strong>{subjectLabel ?? t('evidence.subjectUnresolved')}</strong>
              </div>

              {subjectRegistry && (
                <>
                  <div style={{ marginBottom: 10 }}>
                    <span className="cm-label">{t('evidence.siren')}</span>
                    <span style={{ fontFamily: 'monospace' }}>{subjectRegistry.siren ?? '—'}</span>
                  </div>
                  <div style={{ marginBottom: 10 }}>
                    <span className="cm-label">{t('evidence.rna')}</span>
                    <span style={{ fontFamily: 'monospace' }}>{subjectRegistry.rna ?? '—'}</span>
                  </div>
                  <div style={{ marginBottom: 10 }}>
                    <span className="cm-label">{t('evidence.rnaActive')}</span>
                    <span>{formatTriState(subjectRegistry.rnaActive, t)}</span>
                  </div>
                  <div>
                    <span className="cm-label">{t('evidence.registryCheckedAt')}</span>
                    <span>{formatDate(subjectRegistry.checkedAt, locale)}</span>
                  </div>
                </>
              )}

              {!subjectRegistry && subjectId && (
                <div>
                  <span className="cm-label">{t('evidence.subjectId')}</span>
                  <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{subjectId}</span>
                </div>
              )}
            </div>

            {/* Right: screening conditions */}
            <div>
              <p className="cm-label" style={{ marginBottom: 8 }}>{t('evidence.conditionsSide')}</p>

              <div style={{ marginBottom: 10 }}>
                <span className="cm-label">{t('evidence.algorithm')}</span>
                <span style={{ fontFamily: 'monospace' }}>{algorithm}</span>
              </div>

              <div style={{ marginBottom: 10 }}>
                <span className="cm-label">{t('evidence.threshold')}</span>
                <span style={{ fontFamily: 'monospace' }}>{threshold}</span>
              </div>

              <div style={{ marginBottom: 10 }}>
                <span className="cm-label">{t('evidence.registryPublicationDate')}</span>
                <span>{formatDate(registryDate, locale)}</span>
              </div>

              <div>
                <span className="cm-label">{t('evidence.matchCount')}</span>
                <strong>{matches.length}</strong>
              </div>
            </div>
          </div>

          {/* One block per screened party — a person-name match must never be read as if it had
              scored against the association's name. */}
          {groups.map((group) => (
            <div key={group.key} style={{ marginTop: 20 }}>
              <div className="frow" style={{ flexWrap: 'wrap', gap: 16, alignItems: 'baseline' }}>
                <div>
                  <span className="cm-label">{t('evidence.screenedParty')}</span>
                  <strong>{t(`alerts.subjectType.${group.subjectType}`)}</strong>
                </div>
                <div>
                  <span className="cm-label">{t('evidence.screenedNormalized')}</span>
                  <strong style={{ fontFamily: 'monospace' }}>{group.screenedNormalizedName}</strong>
                </div>
              </div>

              <div style={{ overflowX: 'auto', marginTop: 8 }}>
                <table className="cm-table">
                  <thead>
                    <tr>
                      <th>{t('evidence.col.score')}</th>
                      <th>{t('evidence.col.matchedName')}</th>
                      <th>{t('evidence.col.nature')}</th>
                      <th>{t('evidence.col.idRegistre')}</th>
                      <th>{t('evidence.col.dateOfBirth')}</th>
                      <th>{t('evidence.col.legalReference')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {group.matches.map((m) => (
                      <tr key={`${m.sanctionedIdRegistre}-${m.subjectId}-${m.score}`}>
                        <td style={{ fontFamily: 'monospace' }}>
                          <strong>{formatScore(m.score)}</strong>
                        </td>
                        <td>{m.matchedName}</td>
                        <td>{t(`evidence.nature.${m.matchedNature}`)}</td>
                        <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{m.sanctionedIdRegistre}</td>
                        <td>{m.matchedDateOfBirth ?? '—'}</td>
                        <td style={{ fontSize: 13 }}>{m.matchedLegalReference ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}

          <p style={{ fontSize: 12, color: 'var(--color-text-2)', marginTop: 12 }}>
            {t('evidence.snapshotNote')}
          </p>
        </>
      )}
    </div>
  );
}
