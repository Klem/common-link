import { useTranslations, useLocale } from 'next-intl';
import { AssociationResult } from '@/lib/api';

interface AssociationCardProps {
  association: AssociationResult;
  onSelect: (association: AssociationResult) => void;
  onClick?: (association: AssociationResult) => void;
  animationDelay?: number;
}

export function AssociationCard({ association, onSelect, onClick, animationDelay = 0 }: AssociationCardProps) {
  const t = useTranslations('associations.search');
  const locale = useLocale();

  const name = association.nom_complet || association.nom_raison_sociale || 'Association';
  const initial = name[0].toUpperCase();
  const siege = association.siege || {};
  const ville = siege.libelle_commune || '—';
  const cp = siege.code_postal || '—';
  const dateCreation = association.date_creation
    ? new Date(association.date_creation).toLocaleDateString(locale, {
        year: 'numeric',
        month: 'short',
      })
    : '—';

  return (
    <div
      className="flex flex-col bg-white border border-border rounded-lg overflow-hidden transition-all duration-[250ms] cursor-pointer hover:-translate-y-1 hover:shadow-lg hover:border-secondary"
      style={{
        animation: `cardEnter 0.4s ease ${animationDelay}ms both`,
      }}
      onClick={() => onClick?.(association)}
    >
      {/* Header */}
      <div className="p-6 flex items-start gap-4">
        <div className="w-12 h-12 flex-shrink-0 bg-gradient-to-br from-primary to-secondary rounded-md flex items-center justify-center text-white font-ui font-bold text-[1.1rem]">
          {initial}
        </div>
        <div>
          <div className="font-ui font-semibold text-[0.95rem] text-foreground-dark mb-1 line-clamp-2">
            {name}
          </div>
          <span className="inline-flex items-center gap-1 font-ui text-[0.75rem] font-semibold px-2 py-0.5 rounded-[10px] text-success bg-success/10">
            <span className="w-1.5 h-1.5 rounded-full bg-current" />
            {t('active')}
          </span>
        </div>
      </div>

      {/* Body */}
      <div className="px-6 pb-6 flex-1" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', alignContent: 'start' }}>
        <div>
          <div className="font-ui text-[0.7rem] uppercase tracking-wide text-foreground-muted">
            {t('city')}
          </div>
          <div className="font-ui text-[0.85rem] text-foreground">{ville}</div>
        </div>
        <div>
          <div className="font-ui text-[0.7rem] uppercase tracking-wide text-foreground-muted">
            {t('postalCode')}
          </div>
          <div className="font-ui text-[0.85rem] text-foreground">{cp}</div>
        </div>
        <div>
          <div className="font-ui text-[0.7rem] uppercase tracking-wide text-foreground-muted">
            {t('siren')}
          </div>
          <div className="font-ui text-[0.85rem] text-foreground tabular-nums">
            {association.siren || '—'}
          </div>
        </div>
        <div>
          <div className="font-ui text-[0.7rem] uppercase tracking-wide text-foreground-muted">
            {t('created')}
          </div>
          <div className="font-ui text-[0.85rem] text-foreground">
            {dateCreation}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="mt-auto px-6 py-4 bg-background-alt border-t border-border-light flex justify-between items-center">
        <span className="font-ui text-[0.75rem] text-foreground-muted">
          {siege.departement ? t('dep', { code: siege.departement }) : ''}
        </span>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onSelect(association);
          }}
          className="font-ui text-[0.8rem] font-semibold text-primary bg-transparent border-[1.5px] border-primary px-4 py-1.5 rounded-sm cursor-pointer transition-all duration-200 hover:bg-primary hover:text-white"
        >
          {t('select')}
        </button>
      </div>
    </div>
  );
}
