'use client';

import { useEffect, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { AssociationResult } from '@/lib/api';

interface AssociationDetailModalProps {
  association: AssociationResult | null;
  onClose: () => void;
}

function formatAddress(siege: NonNullable<AssociationResult['siege']>): string {
  const parts: string[] = [];
  if (siege.numero_voie) parts.push(siege.numero_voie);
  if (siege.type_voie) parts.push(siege.type_voie);
  if (siege.libelle_voie) parts.push(siege.libelle_voie);
  if (parts.length === 0 && siege.adresse) return siege.adresse;
  if (parts.length === 0) return '';
  return parts.join(' ');
}

function formatEffectif(code: string | undefined): string | null {
  if (!code) return null;
  const mapping: Record<string, string> = {
    '00': '0',
    '01': '1-2',
    '02': '3-5',
    '03': '6-9',
    '11': '10-19',
    '12': '20-49',
    '21': '50-99',
    '22': '100-199',
    '31': '200-249',
    '32': '250-499',
    '41': '500-999',
    '42': '1000-1999',
    '51': '2000-4999',
    '52': '5000-9999',
    '53': '10000+',
  };
  return mapping[code] || null;
}

export function AssociationDetailModal({ association, onClose }: AssociationDetailModalProps) {
  const t = useTranslations('associations.search.modal');
  const ts = useTranslations('associations.search');

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    },
    [onClose]
  );

  useEffect(() => {
    if (association) {
      document.body.style.overflow = 'hidden';
      document.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.body.style.overflow = '';
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [association, handleKeyDown]);

  if (!association) return null;

  const siege = association.siege || {};
  const isActive = association.etat_administratif === 'A';
  const name = association.nom_complet || association.nom_raison_sociale || 'Association';
  const address = formatAddress(siege);
  const dateCreation = association.date_creation
    ? new Date(association.date_creation).toLocaleDateString('fr-FR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : '—';

  return (
    <div
      className="fixed inset-0 bg-black/60 backdrop-blur-sm z-[1000] flex items-center justify-center p-5"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="bg-white border border-border rounded-xl max-w-[700px] w-full max-h-[90vh] overflow-y-auto shadow-2xl"
        style={{ animation: 'modalEnter 0.3s ease' }}
      >
        {/* Header */}
        <div className="p-8 border-b border-border-light flex justify-between items-start">
          <div>
            <h2 className="font-ui font-bold text-[1.25rem] text-foreground-dark mb-2">
              {name}
            </h2>
            {isActive ? (
              <span className="inline-flex items-center gap-1.5 font-ui text-[0.75rem] font-semibold px-2.5 py-1 rounded-[10px] text-success bg-success/10">
                <span className="w-1.5 h-1.5 rounded-full bg-current" />
                {ts('active')}
              </span>
            ) : (
              <span className="inline-flex items-center gap-1.5 font-ui text-[0.75rem] font-semibold px-2.5 py-1 rounded-[10px] text-error bg-error/10">
                <span className="w-1.5 h-1.5 rounded-full bg-current" />
                {ts('closed')}
              </span>
            )}
          </div>
          <button
            onClick={onClose}
            className="w-10 h-10 rounded-md bg-transparent border border-border text-foreground-muted cursor-pointer transition-colors hover:border-error hover:text-error flex items-center justify-center text-lg"
            aria-label={t('close')}
          >
            ✕
          </button>
        </div>

        {/* Body */}
        <div className="p-8 space-y-6">
          {/* Identification */}
          <div>
            <h4 className="font-ui text-[0.7rem] uppercase tracking-[2px] text-foreground-muted mb-3 pb-2 border-b border-border-light">
              {t('identification')}
            </h4>
            <div className="grid grid-cols-2 gap-5">
              <DetailItem label={ts('siren')} value={association.siren || '—'} mono />
              <DetailItem label={t('siret')} value={siege.siret || '—'} mono />
              <DetailItem label={t('dateCreation')} value={dateCreation} />
              <DetailItem label={t('natureJuridique')} value={association.nature_juridique || '—'} />
            </div>
          </div>

          {/* Address */}
          <div>
            <h4 className="font-ui text-[0.7rem] uppercase tracking-[2px] text-foreground-muted mb-3 pb-2 border-b border-border-light">
              {t('address')}
            </h4>
            <div className="grid grid-cols-2 gap-5">
              <div className="col-span-2">
                <DetailItem label={t('fullAddress')} value={address || t('noAddress')} />
              </div>
              <DetailItem label={t('commune')} value={siege.libelle_commune || '—'} />
              <DetailItem label={ts('postalCode')} value={siege.code_postal || '—'} />
              <DetailItem label={t('department')} value={siege.departement || '—'} />
              <DetailItem label={t('region')} value={siege.region || '—'} />
            </div>
          </div>

          {/* Activity */}
          <div>
            <h4 className="font-ui text-[0.7rem] uppercase tracking-[2px] text-foreground-muted mb-3 pb-2 border-b border-border-light">
              {t('activity')}
            </h4>
            <div className="grid grid-cols-2 gap-5">
              <DetailItem
                label={t('naf')}
                value={association.activite_principale || siege.activite_principale || '—'}
                mono
              />
              <DetailItem
                label={t('activitySection')}
                value={association.section_activite_principale || '—'}
              />
              <DetailItem
                label={t('employees')}
                value={formatEffectif(association.tranche_effectif_salarie) || '—'}
              />
              <DetailItem
                label={t('establishments')}
                value={association.nombre_etablissements?.toString() || '—'}
              />
            </div>
          </div>

          {/* Directors */}
          {association.dirigeants && association.dirigeants.length > 0 && (
            <div>
              <h4 className="font-ui text-[0.7rem] uppercase tracking-[2px] text-foreground-muted mb-3 pb-2 border-b border-border-light">
                {t('directors')}
              </h4>
              <div className="space-y-2">
                {association.dirigeants.slice(0, 3).map((d, i) => (
                  <div key={i} className="p-3 bg-background-alt rounded-md">
                    <div className="font-ui font-semibold text-[0.9rem] text-foreground-dark">
                      {d.prenom || ''} {d.nom || ''}
                    </div>
                    <div className="font-ui text-[0.8rem] text-foreground-muted mt-1">
                      {d.qualite || d.fonction || ''}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-8 py-5 border-t border-border-light flex gap-3 justify-end">
          <button
            onClick={onClose}
            className="font-ui text-[0.85rem] font-medium bg-transparent border border-border text-foreground-muted px-6 py-3 rounded-md cursor-pointer transition-colors hover:border-primary hover:text-primary"
          >
            {t('close')}
          </button>
          <a
            href={`https://annuaire-entreprises.data.gouv.fr/association/${association.siren}`}
            target="_blank"
            rel="noopener noreferrer"
            className="font-ui text-[0.85rem] font-medium bg-primary text-white px-6 py-3 rounded-md cursor-pointer transition-colors hover:bg-primary-light no-underline"
          >
            {t('viewAnnuaire')}
          </a>
        </div>
      </div>
    </div>
  );
}

function DetailItem({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <span className="font-ui text-[0.75rem] text-foreground-muted">{label}</span>
      <span
        className={`font-ui text-[0.9rem] text-foreground-dark ${mono ? 'tabular-nums text-primary' : ''}`}
      >
        {value}
      </span>
    </div>
  );
}
