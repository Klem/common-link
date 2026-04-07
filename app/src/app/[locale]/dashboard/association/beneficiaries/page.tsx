'use client';

import { useState, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { Topbar } from '@/components/dashboard';
import { SirenSearchCard, SireneResultPanel } from '@/components/beneficiary';
import { createBeneficiary } from '@/lib/api/beneficiary';
import { useToastStore } from '@/stores/toastStore';
import type { SireneSearchResultDto, CreateBeneficiaryRequest } from '@/types/beneficiary';

/**
 * Maps a Sirene search result to the create-beneficiary request body.
 * @param result - The Sirene result selected by the user.
 */
function toCreateRequest(result: SireneSearchResultDto): CreateBeneficiaryRequest {
  return {
    name: result.name,
    identifier1: result.siren,
    identifier2: result.siret ?? undefined,
    activityCode: result.nafCode ?? undefined,
    category: result.category ?? undefined,
    city: result.city ?? undefined,
    postalCode: result.postalCode ?? undefined,
    active: result.active,
  };
}

/**
 * Beneficiary management page for associations.
 * Renders the SIREN/SIRET search card and the result confirmation panel.
 */
export default function BeneficiariesPage() {
  const t = useTranslations('dashboard');
  const { addToast } = useToastStore();

  const [sireneResult, setSireneResult] = useState<SireneSearchResultDto | null>(null);
  const [showPanel, setShowPanel] = useState(false);
  const [isCreating, setIsCreating] = useState(false);

  const handleResult = useCallback((result: SireneSearchResultDto) => {
    setSireneResult(result);
    setShowPanel(true);
  }, []);

  const handleClose = () => {
    setShowPanel(false);
  };

  const handleSelect = async () => {
    if (!sireneResult) return;
    setIsCreating(true);
    try {
      await createBeneficiary(toCreateRequest(sireneResult));
      addToast('success', 'beneficiaryCreated');
      setShowPanel(false);
      setSireneResult(null);
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div>
      <Topbar
        title={t('beneficiaries.pageTitle')}
        subtitle={t('beneficiaries.pageSubtitle')}
      />

      <div className="p-[24px]">
        <SirenSearchCard onResult={handleResult} />
      </div>

      {showPanel && sireneResult && (
        <SireneResultPanel
          result={sireneResult}
          onSelect={handleSelect}
          onClose={handleClose}
          isLoading={isCreating}
        />
      )}
    </div>
  );
}
