'use client';

import { useState, useEffect } from 'react';
import { useTranslations, useLocale } from 'next-intl';
import { getRegistryPreCheck, scanRegistryPreCheck } from '@/lib/api/admin';
import type { RegistryPreCheckDto } from '@/types/admin';
import { ScopeVerdict } from '@/types/admin';

interface Props {
  associationId: string;
}

const COLOR_OK = '#27ae60';
const COLOR_WARN = '#e67e22';
const COLOR_ERR = 'var(--color-error)';

function StatusDot({ ok, warn }: { ok: boolean; warn?: boolean }) {
  const color = ok ? COLOR_OK : warn ? COLOR_WARN : COLOR_ERR;
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'inline-block',
        width: 8,
        height: 8,
        borderRadius: '50%',
        background: color,
        marginRight: 6,
        flexShrink: 0,
      }}
    />
  );
}

function Row({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 13 }}>
      {children}
    </div>
  );
}

/**
 * INSEE legal categories (level III) that carry a translated label. Any other code is displayed
 * raw rather than mislabelled — the backend accept-list lives in `ScopeVerdict.kt` and this list
 * only governs how a code is rendered.
 */
const LABELLED_LEGAL_CATEGORIES = [
  '9210', '9220', '9221', '9222', '9223', '9230', '9240', '9260', '9300',
] as const;

function isKnownLegalCategory(code: string): boolean {
  return (LABELLED_LEGAL_CATEGORIES as readonly string[]).includes(code);
}

function Source({ name }: { name: string }) {
  return (
    <span style={{ color: 'var(--color-text-2)', fontSize: 12, marginLeft: 2 }}>
      ({name})
    </span>
  );
}

export function RegistryPreCheckBanner({ associationId }: Props) {
  const t = useTranslations('admin');
  const tc = useTranslations('curator.dossier');
  const locale = useLocale();

  const [data, setData] = useState<RegistryPreCheckDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  // On mount, load the latest persisted scan — a pure read, no external registry call.
  useEffect(() => {
    let active = true;
    setLoading(true);
    setFailed(false);
    getRegistryPreCheck(associationId)
      .then((d) => { if (active) setData(d); })
      .catch(() => { if (active) setFailed(true); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [associationId]);

  // Run a fresh live scan: queries the registries and persists the result (append-only).
  const runScan = () => {
    setLoading(true);
    setFailed(false);
    scanRegistryPreCheck(associationId)
      .then(setData)
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  };

  const refreshBtn = (
    <button
      onClick={runScan}
      disabled={loading}
      style={{
        fontSize: 12,
        padding: '2px 10px',
        border: '1px solid var(--color-border)',
        borderRadius: 4,
        background: 'transparent',
        cursor: loading ? 'default' : 'pointer',
        color: 'var(--color-text-2)',
        opacity: loading ? 0.5 : 1,
      }}
    >
      {data ? t('registryCheck.refresh') : t('registryCheck.scan')}
    </button>
  );

  const containerStyle: React.CSSProperties = {
    borderWidth: 1,
    borderStyle: 'solid',
    borderColor: 'var(--color-border)',
    borderRadius: 8,
    padding: '12px 16px',
    marginBottom: 24,
    fontSize: 13,
  };

  if (!data && !loading && !failed) {
    return (
      <div style={containerStyle}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontWeight: 700, fontSize: 13 }}>{t('registryCheck.title')}</span>
          {refreshBtn}
        </div>
        <p style={{ color: 'var(--color-text-2)', margin: '8px 0 0', fontSize: 12 }}>
          {t('registryCheck.neverChecked')}
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div style={containerStyle}>
        <p style={{ color: 'var(--color-text-2)', margin: 0 }}>{t('registryCheck.loading')}</p>
      </div>
    );
  }

  if (failed || !data) {
    return (
      <div style={containerStyle}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <p style={{ color: 'var(--color-text-2)', margin: 0 }}>{t('registryCheck.error')}</p>
          {refreshBtn}
        </div>
      </div>
    );
  }

  const isCeased = data.etatAdministratif === 'C';
  const isDissolved = data.dissolutionDetected === true;
  const hasInsolvency = data.bodaccProcedureFound === true;
  const hasAnyRisk = isCeased || isDissolved || hasInsolvency || data.associationExists === false;
  const hasWarnings = data.warnings.length > 0;

  const borderColor = hasAnyRisk ? COLOR_ERR : hasWarnings ? COLOR_WARN : 'var(--color-border)';

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleDateString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });

  // `associationExists === null` has two very different causes and must not be shown as one:
  // the source errored (a warning is recorded), or the entity is simply absent from a register
  // that only lists SIREN-bearing entities — which proves nothing about an association.
  const rechercheFailed = data.warnings.some((w) => w.startsWith('recherche-entreprises:'));

  // Determine which checks were skipped and why
  const skipped: Array<{ sources: string[]; reason: string }> = [];

  if (data.siren === null) {
    const reason = rechercheFailed
      ? t('registryCheck.skipped.unavailable')
      : t('registryCheck.skipped.noSiren');
    skipped.push({ sources: ['INSEE Sirene', 'BODACC'], reason });
  }

  if (data.rna === null) {
    skipped.push({ sources: ['JOAFE'], reason: t('registryCheck.skipped.noRna') });
  }

  return (
    <div style={{ ...containerStyle, borderColor }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <span style={{ fontWeight: 700, fontSize: 13 }}>{t('registryCheck.title')}</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontSize: 12, color: 'var(--color-text-2)' }}>
            {t('registryCheck.checkedAt')} {formatDate(data.checkedAt)}
          </span>
          {refreshBtn}
        </div>
      </div>

      {/* Identifiers */}
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 8 }}>
        {data.siren && (
          <span style={{ fontFamily: 'monospace' }}>
            {t('registryCheck.siren')}: {data.siren}
          </span>
        )}
        {data.rna && (
          <span style={{ fontFamily: 'monospace' }}>
            {t('registryCheck.rna')}: {data.rna}
          </span>
        )}
      </div>

      {/* Status rows — performed checks */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
        <Row>
          <StatusDot ok={data.associationExists === true} warn={data.associationExists === null} />
          <span>
            {data.associationExists === true
              ? t('registryCheck.status.active')
              : data.associationExists === false
              ? t('registryCheck.status.notFound')
              : rechercheFailed
              ? t('registryCheck.error')
              : t('registryCheck.status.notListed')}
          </span>
          <Source name="Recherche entreprises" />
        </Row>

        {data.etatAdministratif !== null && (
          <Row>
            <StatusDot ok={data.etatAdministratif === 'A'} />
            <span>
              {data.etatAdministratif === 'A'
                ? t('registryCheck.insee.active')
                : t('registryCheck.insee.ceased')}
            </span>
            <Source name="INSEE Sirene" />
          </Row>
        )}

        {data.joafeDeclarationFound !== null && (
          <Row>
            <StatusDot
              ok={data.joafeDeclarationFound === true && data.dissolutionDetected !== true}
              warn={data.dissolutionDetected === true}
            />
            <span>
              {data.dissolutionDetected === true
                ? t('registryCheck.status.dissolved')
                : data.joafeDeclarationFound
                ? t('registryCheck.joafe.declaration')
                : t('registryCheck.joafe.noDeclaration')}
            </span>
            <Source name="JOAFE" />
          </Row>
        )}

        {data.bodaccProcedureFound !== null && (
          <Row>
            <StatusDot ok={!data.bodaccProcedureFound} />
            <span>
              {data.bodaccProcedureFound
                ? t('registryCheck.status.insolvency')
                : t('registryCheck.bodacc.clean')}
            </span>
            <Source name="BODACC" />
          </Row>
        )}

        {/* Skipped checks */}
        {skipped.length > 0 && (
          <div style={{ marginTop: 4, paddingTop: 6, borderTop: '1px dashed var(--color-border)' }}>
            <span style={{ color: 'var(--color-text-2)', fontSize: 12, fontWeight: 600 }}>
              {t('registryCheck.skipped.title')}
            </span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 3, marginTop: 4 }}>
              {skipped.map(({ sources, reason }) => (
                <div
                  key={sources.join(',')}
                  style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--color-text-2)' }}
                >
                  <span style={{ fontWeight: 600, fontFamily: 'monospace' }}>{sources.join(', ')}</span>
                  {' — '}
                  {reason}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Scope verdict — loi 1901 */}
      <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px dashed var(--color-border)' }}>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--color-text-2)' }}>
          {tc('registry.scopeVerdict.title')}:{' '}
        </span>
        {data.scopeVerdict === ScopeVerdict.IN_SCOPE && (
          <span style={{ color: COLOR_OK, fontWeight: 600, fontSize: 13 }}>
            {tc('registry.scopeVerdict.IN_SCOPE')}
          </span>
        )}
        {data.scopeVerdict === ScopeVerdict.OUT_OF_SCOPE && (
          <>
            <span style={{ color: COLOR_ERR, fontWeight: 600, fontSize: 13 }}>
              {tc('registry.scopeVerdict.OUT_OF_SCOPE')}
            </span>
            <p style={{ margin: '4px 0 0', fontSize: 12, color: COLOR_ERR }}>
              {tc('registry.scopeVerdict.outOfScopeWarning')}
            </p>
          </>
        )}
        {data.scopeVerdict === ScopeVerdict.UNDETERMINED && (
          <>
            <span style={{ color: 'var(--color-text-2)', fontSize: 13 }}>
              {tc('registry.scopeVerdict.UNDETERMINED')}
            </span>
            <p style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--color-text-2)' }}>
              {tc('registry.scopeVerdict.undeterminedHint')}
            </p>
          </>
        )}

        {/* Which INSEE legal category the verdict was derived from */}
        <p style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--color-text-2)' }}>
          {tc('registry.legalCategory.title')}:{' '}
          {data.legalCategory === null ? (
            <em>{tc('registry.legalCategory.unknown')}</em>
          ) : (
            <>
              <span style={{ fontFamily: 'monospace' }}>{data.legalCategory}</span>
              {isKnownLegalCategory(data.legalCategory) &&
                ` — ${tc(`registry.legalCategory.codes.${data.legalCategory}`)}`}
            </>
          )}
        </p>

        {data.scopeVerdict !== ScopeVerdict.IN_SCOPE && (
          <p style={{ margin: '2px 0 0', fontSize: 11, color: 'var(--color-text-2)' }}>
            {tc('registry.scopeVerdict.accepted')}
          </p>
        )}
      </div>

      {/* RNA active status */}
      {data.rnaActive !== undefined && (
        <div style={{ marginTop: 6, fontSize: 13 }}>
          <Row>
            <StatusDot
              ok={data.rnaActive === true}
              warn={data.rnaActive === null}
            />
            <span>
              {data.rnaActive === true
                ? tc('registry.rnaActive.true')
                : data.rnaActive === false
                ? tc('registry.rnaActive.false')
                : tc('registry.rnaActive.unknown')}
            </span>
            <Source name="RNA" />
          </Row>
        </div>
      )}

      {/* Officers */}
      <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px dashed var(--color-border)' }}>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--color-text-2)' }}>
          {tc('registry.officers.title')}
        </span>
        {data.officers.length === 0 ? (
          <p style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--color-text-2)', fontStyle: 'italic' }}>
            {tc('registry.officers.empty')}
          </p>
        ) : (
          <ul style={{ margin: '4px 0 0', paddingLeft: 16, fontSize: 13 }}>
            {data.officers.map((name) => (
              <li key={name} style={{ marginBottom: 2 }}>{name}</li>
            ))}
          </ul>
        )}
      </div>

      {/* Warnings */}
      {data.warnings.length > 0 && (
        <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--color-border)' }}>
          <span style={{ color: COLOR_WARN, fontWeight: 600 }}>{t('registryCheck.warnings')}: </span>
          <span style={{ color: 'var(--color-text-2)' }}>{data.warnings.join(' · ')}</span>
        </div>
      )}

      {/* Note */}
      <p style={{ margin: '8px 0 0', color: 'var(--color-text-2)', fontSize: 12, fontStyle: 'italic' }}>
        {t('registryCheck.note')}
      </p>
    </div>
  );
}
