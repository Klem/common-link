'use client';

import { useEffect } from 'react';
import { useTranslations } from 'next-intl';
import type { SireneSearchResultDto } from '@/types/payee';

interface SireneResultPanelProps {
  /** The Sirene search result to display. */
  result: SireneSearchResultDto;
  /** Called when the user clicks "Confirmer ce bénéficiaire". */
  onSelect: () => void;
  /** Called when the user closes the panel. */
  onClose: () => void;
  /** When true, the confirm button shows a spinner (creation in progress). */
  isLoading?: boolean;
}

/**
 * Inline result panel displaying key information from a Sirene search result.
 *
 * Shows: name, status badges, SIREN/address info in a DL grid.
 * Confirm adds the payee; Cancel/Escape dismisses the panel.
 */
export function SireneResultPanel({ result, onSelect, onClose, isLoading = false }: SireneResultPanelProps) {
  const t = useTranslations('dashboard');

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const address = [result.city, result.postalCode].filter(Boolean).join(' — ');

  return (
    <div className="card card-no-hover animate-slide-up-step">
      {/* Header bar: name + status badges */}
      <div className="card-header-bar">
        <div className="flex-1 min-w-0">
          <h2 className="font-display font-bold text-lg text-text leading-tight truncate">
            {result.name}
          </h2>
          <div className="flex flex-wrap gap-1.5 mt-2">
            {result.active ? (
              <span className="badge badge-active">● {t('payees.result.active')}</span>
            ) : (
              <span className="badge badge-neutral">● {t('payees.result.closed')}</span>
            )}
            {result.category && <span className="badge badge-neutral">{result.category}</span>}
            {result.isEss && <span className="badge badge-warning">{t('payees.result.ess')}</span>}
            {result.isSiege && <span className="badge badge-info">{t('payees.result.siege')}</span>}
          </div>
        </div>
      </div>

      {/* Body: DL grid + address + actions */}
      <div className="card-body">
        <dl className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <dt className="text-text-2 text-xs uppercase tracking-wide">{t('payees.result.siren')}</dt>
            <dd className="font-mono font-semibold text-sm text-text mt-0.5">{result.siren}</dd>
          </div>
          {result.creationDate && (
            <div>
              <dt className="text-text-2 text-xs uppercase tracking-wide">{t('payees.result.created')}</dt>
              <dd className="font-semibold text-sm text-text mt-0.5">{result.creationDate}</dd>
            </div>
          )}
          {result.nafCode && (
            <div>
              <dt className="text-text-2 text-xs uppercase tracking-wide">{t('payees.result.naf')}</dt>
              <dd className="font-mono font-semibold text-sm text-text mt-0.5">{result.nafCode}</dd>
            </div>
          )}
          {result.employeeRange && (
            <div>
              <dt className="text-text-2 text-xs uppercase tracking-wide">{t('payees.result.employees')}</dt>
              <dd className="font-semibold text-sm text-text mt-0.5">{result.employeeRange}</dd>
            </div>
          )}
          {address && (
            <div className="md:col-span-2">
              <dt className="text-text-2 text-xs uppercase tracking-wide">{t('payees.result.address')}</dt>
              <dd className="text-sm text-text mt-0.5">📍 {address}</dd>
            </div>
          )}
        </dl>

        {/* Action buttons */}
        <div className="flex gap-3 mt-5">
          <button
            onClick={onSelect}
            disabled={isLoading}
            className="btn btn-primary btn-md flex items-center gap-2"
            aria-busy={isLoading}
          >
            {isLoading ? (
              <span className="inline-block w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              '✚'
            )}
            {t('payees.result.select')}
          </button>
          <button onClick={onClose} className="btn btn-ghost btn-md">
            {t('payees.result.close')}
          </button>
        </div>
      </div>
    </div>
  );
}
