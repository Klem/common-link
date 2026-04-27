'use client';

import { useState, useCallback } from 'react';
import { useTranslations } from 'next-intl';
import { Topbar } from '@/components/dashboard';
import { SirenSearchCard, SireneResultPanel, PayeeList } from '@/components/payee';
import { createPayee } from '@/lib/api/payee';
import { usePayees } from '@/hooks/payee/usePayees';
import { useVopVerify } from '@/hooks/payee/useVopVerify';
import { useToastStore } from '@/stores/toastStore';
import type { SireneSearchResultDto, CreatePayeeRequest } from '@/types/payee';

/**
 * Maps a Sirene search result to the create-payee request body.
 * @param result - The Sirene result selected by the user.
 */
function toCreateRequest(result: SireneSearchResultDto): CreatePayeeRequest {
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
 * Payee management page for associations.
 *
 * Assembles the SIREN/SIRET search card, the result confirmation overlay,
 * and the payee list with IBAN management and VOP verification.
 */
export default function PayeesPage() {
  const t = useTranslations('dashboard');
  const { addToast } = useToastStore();

  const {
    payees,
    isLoading,
    fetchPayees,
    addPayeeIban,
    removePayeeIban,
    removePayee,
  } = usePayees();

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
      await createPayee(toCreateRequest(sireneResult));
      addToast('success', 'payeeCreated');
      setShowPanel(false);
      setSireneResult(null);
      await fetchPayees();
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsCreating(false);
    }
  };

  const handleAddIban = async (payeeId: string, iban: string) => {
    try {
      await addPayeeIban(payeeId, iban);
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const handleDeleteIban = async (payeeId: string, ibanId: string) => {
    try {
      await removePayeeIban(payeeId, ibanId);
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const handleDeletePayee = async (id: string) => {
    try {
      await removePayee(id);
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const handleVerifyVop = async (payeeId: string, ibanId: string) => {
    await verify(payeeId, ibanId, fetchPayees);
  };

  return (
    <div>
      <Topbar
        title={t('payees.pageTitle')}
        subtitle={t('payees.pageSubtitle')}
      />

      <div className="p-[24px] flex flex-col gap-[20px]">
        <SirenSearchCard onResult={handleResult} />

        <PayeeList
          payees={payees}
          isLoading={isLoading}
          onDeletePayee={handleDeletePayee}
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
