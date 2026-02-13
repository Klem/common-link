'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { AssociationResult } from '@/lib/api';
import { FormInput } from '@/components/ui/FormInput';
import { Button } from '@/components/ui/Button';

interface RegistrationFormProps {
  association: AssociationResult;
  onChangeAssociation: () => void;
}

export function RegistrationForm({
  association,
  onChangeAssociation,
}: RegistrationFormProps) {
  const t = useTranslations('associations.form');
  const ts = useTranslations('associations.search');
  const [submitted, setSubmitted] = useState(false);

  const name = association.nom_complet || association.nom_raison_sociale || '—';
  const initial = name[0].toUpperCase();
  const siege = association.siege || {};

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
  };

  if (submitted) {
    return (
      <div className="max-w-[540px] mx-auto mt-12 bg-white border border-border rounded-xl p-12 shadow-lg">
        <div className="text-center py-8">
          <div className="text-5xl mb-6">🎉</div>
          <h3 className="mb-4">{t('selectedTitle')}</h3>
          <p className="text-foreground-muted max-w-[380px] mx-auto">
            {t('success')}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-[540px] mx-auto mt-12 bg-white border border-border rounded-xl p-12 shadow-lg">
      {/* Selected association header */}
      <div className="flex items-center gap-4 mb-6 pb-4 border-b border-border-light">
        <div className="w-11 h-11 flex-shrink-0 bg-gradient-to-br from-primary to-secondary rounded-md flex items-center justify-center text-white font-ui font-bold text-base">
          {initial}
        </div>
        <div className="flex-1">
          <div className="font-ui font-bold text-foreground-dark">{name}</div>
          <div className="font-ui text-[0.8rem] text-foreground-muted">
            {ts('siren')} {association.siren} · {siege.libelle_commune || ''}{' '}
            {siege.code_postal || ''}
          </div>
        </div>
        <button
          onClick={onChangeAssociation}
          className="font-ui text-[0.8rem] text-foreground-muted bg-transparent border border-border px-3 py-1 rounded-sm cursor-pointer hover:border-primary hover:text-primary transition-colors"
        >
          {t('change')}
        </button>
      </div>

      <h3 className="text-center mb-6">{t('selectedTitle')}</h3>

      <form onSubmit={handleSubmit}>
        <FormInput
          label={t('responsibleName')}
          name="contact-name"
          placeholder={t('responsibleNamePlaceholder')}
          required
        />
        <FormInput
          label={t('email')}
          name="email"
          type="email"
          placeholder={t('emailPlaceholder')}
          required
        />
        <FormInput
          label={t('description')}
          name="description"
          placeholder={t('descriptionPlaceholder')}
        />
        <Button
          type="submit"
          variant="accent"
          size="lg"
          className="w-full justify-center"
        >
          {t('submit')}
        </Button>
      </form>

      <p className="font-ui text-[0.78rem] text-foreground-muted text-center mt-6 leading-relaxed">
        {t('note')}
      </p>
    </div>
  );
}
