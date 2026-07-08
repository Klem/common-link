'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  getVerificationState,
  uploadVerificationDocument,
  deleteVerificationDocument,
  submitVerification as apiSubmitVerification,
  listOptionalDocuments,
  uploadOptionalDocument,
  deleteOptionalDocument,
} from '@/lib/api/verification';
import { useToastStore } from '@/stores/toastStore';
import type { VerificationStateDto, VerificationDocType, OptionalDocumentDto } from '@/types/verification';

interface UseVerificationReturn {
  state: VerificationStateDto | null;
  optionalDocs: OptionalDocumentDto[];
  isLoading: boolean;
  uploadRequired: (docType: VerificationDocType, file: File) => Promise<void>;
  deleteRequired: (docType: VerificationDocType) => Promise<void>;
  submitDossier: () => Promise<void>;
  uploadOptional: (file: File, category: string) => Promise<void>;
  deleteOptional: (id: string) => Promise<void>;
}

export function useVerification(): UseVerificationReturn {
  const [state, setState] = useState<VerificationStateDto | null>(null);
  const [optionalDocs, setOptionalDocs] = useState<OptionalDocumentDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { addToast } = useToastStore();

  const load = useCallback(async () => {
    try {
      const [verif, docs] = await Promise.all([getVerificationState(), listOptionalDocuments()]);
      setState(verif);
      setOptionalDocs(docs);
    } catch {
      addToast('error', 'errors.serverError');
    } finally {
      setIsLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    load();
  }, [load]);

  const uploadRequired = async (docType: VerificationDocType, file: File) => {
    try {
      await uploadVerificationDocument(docType, file);
      addToast('success', 'verificationDocUploaded');
      await load();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const deleteRequired = async (docType: VerificationDocType) => {
    try {
      await deleteVerificationDocument(docType);
      addToast('success', 'verificationDocDeleted');
      await load();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const submitDossier = async () => {
    try {
      await apiSubmitVerification();
      addToast('success', 'verificationSubmitted');
      await load();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const uploadOptional = async (file: File, category: string) => {
    try {
      await uploadOptionalDocument(file, category);
      addToast('success', 'verificationOptDocUploaded');
      await load();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  const deleteOptional = async (id: string) => {
    try {
      await deleteOptionalDocument(id);
      addToast('success', 'verificationOptDocDeleted');
      await load();
    } catch {
      addToast('error', 'errors.serverError');
    }
  };

  return { state, optionalDocs, isLoading, uploadRequired, deleteRequired, submitDossier, uploadOptional, deleteOptional };
}
