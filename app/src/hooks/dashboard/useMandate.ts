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
  sign: (request: SignMandateRequest) => Promise<void>;
  revoke: () => Promise<void>;
  downloadPdf: () => Promise<void>;
}

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

  const sign = async (request: SignMandateRequest) => {
    try {
      const updated = await apiSignMandate(request);
      setState(updated);
      addToast('success', 'mandateSigned');
    } catch {
      addToast('error', 'errors.serverError');
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

  return { state, isLoading, uploadDoc, deleteDoc, sign, revoke, downloadPdf };
}
