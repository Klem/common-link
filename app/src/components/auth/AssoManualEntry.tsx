'use client';

import { useTranslations } from 'next-intl';
import { useAssociationLookup } from '@/hooks/auth/useAssociationLookup';
import type { AssoResult } from './AssoSearch';

interface AssoManualEntryProps {
  /** Called with the confirmed registry identity. `identifier` carries the SIREN on this path. */
  onSelect: (asso: AssoResult) => void;
  /** Returns to the RNA search. */
  onBack: () => void;
}

/**
 * Sign-up entry point for associations that hold a SIREN but no RNA.
 *
 * Those associations are absent from the RNA (JOAFE) search, which would otherwise leave them
 * unable to create an account. The user types a SIREN, the backend confirms it against the
 * *Recherche d'entreprises* registry, and the identity is displayed for confirmation.
 *
 * Fields are read-only on purpose: the RNA flow does not let the user rewrite the JOAFE record
 * either, so identity stays registry-sourced on both paths.
 *
 * Emits the same {@link AssoResult} shape as the RNA search — with the SIREN as `identifier` —
 * so every later sign-up step (auth method, magic link, email/password) is reached unchanged.
 */
export function AssoManualEntry({ onSelect, onBack }: AssoManualEntryProps) {
  const t = useTranslations('auth');
  const { siren, setSiren, result, isLoading, error, isComplete, lookup } = useAssociationLookup();

  const handleConfirm = () => {
    if (!result) return;
    onSelect({
      identifier: result.siren,
      nom: result.name,
      ville: result.city ?? '',
      codePostal: result.postalCode ?? '',
    });
  };

  return (
    <div className="flex flex-col gap-3">
      <p className="text-[12.5px] text-text-2 leading-[1.65]">{t('assoManual.hint')}</p>

      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void lookup();
        }}
      >
        <input
          type="text"
          inputMode="numeric"
          value={siren}
          onChange={(e) => setSiren(e.target.value)}
          placeholder={t('assoManual.placeholder')}
          autoComplete="off"
          aria-label={t('assoManual.label')}
          className="form-input flex-1"
        />
        <button
          type="submit"
          disabled={!isComplete || isLoading}
          className="px-[14px] py-[9px] rounded-[8px] font-body text-[12.5px] font-semibold text-green flex-shrink-0 cursor-pointer transition-all duration-200 hover:opacity-80 bg-green/10 border border-green/25 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {isLoading ? t('assoManual.searching') : t('assoManual.search')}
        </button>
      </form>

      {error && <p className="text-[12px] text-red">{t(error as Parameters<typeof t>[0])}</p>}

      {result && (
        <div className="flex flex-col gap-[10px] bg-bg-3 border border-green/20 rounded-[10px] px-[14px] py-[13px]">
          <div className="flex items-center gap-[11px]">
            <div className="w-9 h-9 rounded-[9px] flex items-center justify-center font-display font-extrabold text-[15px] text-green flex-shrink-0 bg-green/10 border border-green/20">
              {result.name[0]?.toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-[12.5px] font-semibold text-text truncate">{result.name}</div>
              <div className="text-[11px] text-muted flex flex-wrap gap-2">
                {(result.city || result.postalCode) && (
                  <span>📍 {result.city} {result.postalCode}</span>
                )}
                <span>SIREN {result.siren}</span>
                {result.rna && <span>RNA {result.rna}</span>}
              </div>
            </div>
          </div>

          {/* Informational only — eligibility is decided by the curator registry pre-check at KYC. */}
          {!result.isAssociation && (
            <p className="text-[11.5px] text-amber">⚠️ {t('assoManual.notAssociation')}</p>
          )}
          {!result.active && (
            <p className="text-[11.5px] text-amber">⚠️ {t('assoManual.inactive')}</p>
          )}

          <button
            type="button"
            onClick={handleConfirm}
            className="self-start px-[11px] py-[5px] rounded-[6px] font-body text-[12px] font-semibold text-green cursor-pointer transition-all duration-200 hover:opacity-80 bg-green/10 border border-green/25"
          >
            {t('assoManual.confirm')} →
          </button>
        </div>
      )}

      <button
        type="button"
        onClick={onBack}
        className="self-start text-[11.5px] text-cyan bg-transparent border-none cursor-pointer p-0 underline-offset-2 hover:underline"
      >
        ← {t('assoManual.backToRna')}
      </button>
    </div>
  );
}
