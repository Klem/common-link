'use client';

import { useState, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { Topbar } from '@/components/dashboard';
import { SirenSearchCard, SireneResultPanel, BeneficiaryList } from '@/components/beneficiary';
import { createBeneficiary } from '@/lib/api/beneficiary';
import { useBeneficiaries } from '@/hooks/beneficiary/useBeneficiaries';
import { useVopVerify } from '@/hooks/beneficiary/useVopVerify';
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
 *
 * Assembles the SIREN/SIRET search card, the result confirmation overlay,
 * and the beneficiary list with IBAN management and VOP verification.
 */
export default function BeneficiariesPage() {
  const t = useTranslations('dashboard');
  const { addToast } = useToastStore();

  const {
    beneficiaries,
    isLoading,
    fetchBeneficiaries,
    addBeneficiaryIban,
    removeBeneficiaryIban,
    removeBeneficiary,
  } = useBeneficiaries();

  const { verifyingIbanId, verify } = useVopVerify();

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
      await fetchBeneficiaries();
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsCreating(false);
    }
  };

  const handleAddIban = async (beneficiaryId: string, iban: string) => {
    try {
      await addBeneficiaryIban(beneficiaryId, iban);
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const handleDeleteIban = async (beneficiaryId: string, ibanId: string) => {
    try {
      await removeBeneficiaryIban(beneficiaryId, ibanId);
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const handleDeleteBeneficiary = async (id: string) => {
    try {
      await removeBeneficiary(id);
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const handleVerifyVop = async (beneficiaryId: string, ibanId: string) => {
    await verify(beneficiaryId, ibanId, fetchBeneficiaries);
  };

  return (
    <div>
      <Topbar
        title={t('beneficiaries.pageTitle')}
        subtitle={t('beneficiaries.pageSubtitle')}
      />

      <div className="p-[24px] flex flex-col gap-[20px]">
        <SirenSearchCard onResult={handleResult} />

        <BeneficiaryList
          beneficiaries={beneficiaries}
          isLoading={isLoading}
          onDeleteBeneficiary={handleDeleteBeneficiary}
          onAddIban={handleAddIban}
          onDeleteIban={handleDeleteIban}
          onVerifyVop={handleVerifyVop}
          verifyingIbanId={verifyingIbanId}
        />
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
