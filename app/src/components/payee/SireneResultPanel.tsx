'use client';

import { useEffect } from 'react';
import { useTranslations } from 'next-intl';
import type { SireneSearchResultDto } from '@/types/payee';

interface SireneResultPanelProps {
  /** The Sirene search result to display. */
  result: SireneSearchResultDto;
  /** Called when the user clicks "Sélectionner". */
  onSelect: () => void;
  /** Called when the user closes the panel. */
  onClose: () => void;
  /** When true, the "Sélectionner" button shows a spinner (creation in progress). */
  isLoading?: boolean;
}

/**
 * Full-screen overlay panel displaying key information from a Sirene search result.
 *
 * Shows: name, status/category badges, SIREN stats row, address.
 * Supports closing via Escape key, backdrop click, or the "Fermer" button.
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
    <div
      className="fixed inset-0 bg-black/50 backdrop-blur-[6px] z-50 flex items-center justify-center p-4"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      {/* Panel — stop click propagation so clicking inside doesn't close */}
      <div
        className="bg-bg-2 border border-border rounded-xl p-[28px] max-w-[600px] w-full max-h-[80vh] overflow-y-auto animate-slide-up-step"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          {/* Left — name + badges */}
          <div className="min-w-0">
            <h2 className="font-display font-extrabold text-[21px] text-text leading-tight truncate">
              {result.name}
            </h2>
            <div className="flex flex-wrap gap-[6px] mt-[8px]">
              {result.active ? (
                <Badge color="green">● {t('payees.result.active')}</Badge>
              ) : (
                <Badge color="red">● {t('payees.result.closed')}</Badge>
              )}
              {result.category && <Badge color="yellow">{result.category}</Badge>}
              {result.isEss && <Badge color="yellow">{t('payees.result.ess')}</Badge>}
              {result.isSiege && <Badge color="cyan">{t('payees.result.siege')}</Badge>}
            </div>
          </div>

          {/* Right — action buttons */}
          <div className="flex gap-[8px] flex-shrink-0 mt-[4px]">
            <button
              onClick={onSelect}
              disabled={isLoading}
              className={[
                'bg-green text-black font-display font-bold rounded-[8px] px-4 py-2 text-[13px] flex items-center gap-2 transition-opacity',
                isLoading ? 'opacity-60 cursor-not-allowed' : 'hover:opacity-90',
              ].join(' ')}
              aria-busy={isLoading}
            >
              {isLoading ? (
                <span className="inline-block w-4 h-4 border-2 border-black/30 border-t-black rounded-full animate-spin" />
              ) : (
                '✚'
              )}
              {t('payees.result.select')}
            </button>
            <button
              onClick={onClose}
              className="bg-transparent border border-border text-text-2 font-display font-bold rounded-[8px] px-4 py-2 text-[13px] hover:text-text transition-colors"
            >
              ✕ {t('payees.result.close')}
            </button>
          </div>
        </div>

        {/* Stats row */}
        <div className="flex flex-wrap gap-[8px] mt-[18px]">
          <StatChip label={t('payees.result.siren')} value={result.siren} mono cyan />
          {result.creationDate && (
            <StatChip label={t('payees.result.created')} value={result.creationDate} />
          )}
          {result.nafCode && (
            <StatChip label={t('payees.result.naf')} value={result.nafCode} mono />
          )}
          {result.employeeRange && (
            <StatChip label={t('payees.result.employees')} value={result.employeeRange} />
          )}
        </div>

        {/* Address */}
        {address && (
          <div className="mt-[16px] flex items-center gap-[6px] text-[13px] text-text-2">
            <span>📍</span>
            <span>{address}</span>
          </div>
        )}
      </div>
    </div>
  );
}

/** Internal badge component for status/category indicators. */
function Badge({
  color,
  children,
}: {
  color: 'green' | 'red' | 'yellow' | 'cyan';
  children: React.ReactNode;
}) {
  const colorClass = {
    green: 'bg-green/10 text-green border-green/20',
    red: 'bg-red/10 text-red border-red/20',
    yellow: 'bg-yellow/10 text-yellow border-yellow/20',
    cyan: 'bg-cyan/10 text-cyan border-cyan/20',
  }[color];

  return (
    <span
      className={`px-[8px] py-[3px] rounded-full text-[10px] font-bold uppercase border ${colorClass}`}
    >
      {children}
    </span>
  );
}

/** Internal stat chip displaying a label/value pair. */
function StatChip({
  label,
  value,
  mono = false,
  cyan = false,
}: {
  label: string;
  value: string;
  mono?: boolean;
  cyan?: boolean;
}) {
  return (
    <div className="bg-bg-3 border border-border rounded-[8px] px-[10px] py-[6px] flex items-center gap-[6px]">
      <span className="text-[10px] text-text-2 uppercase tracking-[0.05em]">{label}</span>
      <span className={`text-[12px] font-semibold ${mono ? 'font-mono' : ''} ${cyan ? 'text-cyan' : 'text-text'}`}>
        {value}
      </span>
    </div>
  );
}
