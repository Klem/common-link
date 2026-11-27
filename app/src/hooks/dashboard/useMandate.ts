'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  getMandateState,
  uploadMandateDocument,
  deleteMandateDocument,
  signMandate as apiSignMandate,
  revokeMandate as apiRevokeMandate,
  downloadMandatePdf,
} from '@/lib/api/mandate';
import { useToastStore } from '@/stores/toastStore';
import type { MandateStateDto, MandateDocType, SignMandateRequest } from '@/types/mandate';

interface UseMandateReturn {
  state: MandateStateDto | null;
  isLoading: boolean;
  uploadDoc: (docType: MandateDocType, file: File) => Promise<void>;
  deleteDoc: (docType: MandateDocType) => Promise<void>;
  /**
   * Signs the mandate.
   *
   * @return true on success; false when the API refused it (the state is then re-read, so a
   *   prerequisite that went missing meanwhile shows up in the signing gate).
   */
  sign: (request: SignMandateRequest) => Promise<boolean>;
  revoke: () => Promise<void>;
  downloadPdf: () => Promise<void>;
  /** Re-reads the mandate state silently (no loading flag), e.g. after the profile or the signer changed. */
  refresh: () => Promise<void>;
}

/** Loads the fiscal mandate state of the authenticated association and exposes its actions. */
export function useMandate(): UseMandateReturn {
  const [state, setState] = useState<MandateStateDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const { addToast } = useToastStore();

  const load = useCallback(async () => {
    try {
      setState(await getMandateState());
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    load();
  }, [load]);

  const uploadDoc = async (docType: MandateDocType, file: File) => {
    try {
      await uploadMandateDocument(docType, file);
      addToast('success', 'mandateDocUploaded');
      await load();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const deleteDoc = async (docType: MandateDocType) => {
    try {
      await deleteMandateDocument(docType);
      addToast('success', 'mandateDocDeleted');
      await load();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const sign = async (request: SignMandateRequest): Promise<boolean> => {
    try {
      const updated = await apiSignMandate(request);
      setState(updated);
      addToast('success', 'mandateSigned');
      return true;
    } catch {
      addToast('error', 'errors.serverError');
      await load();
      return false;
    }
  };

  const revoke = async () => {
    try {
      await apiRevokeMandate();
      addToast('success', 'mandateRevoked');
      await load();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const downloadPdf = async () => {
    try {
      await downloadMandatePdf();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  return { state, isLoading, uploadDoc, deleteDoc, sign, revoke, downloadPdf, refresh: load };
}
