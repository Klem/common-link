import api from '@/lib/api';
import type {
  VerificationStateDto,
  VerificationDocType,
  OptionalDocumentDto,
} from '@/types/verification';

export const getVerificationState = (): Promise<VerificationStateDto> =>
  api.get<VerificationStateDto>('/api/association/verification').then((r) => r.data);

export const uploadVerificationDocument = (
  docType: VerificationDocType,
  file: File,
): Promise<void> => {
  const form = new FormData();
  form.append('file', file);
  return api.put(`/api/association/verification/documents/${docType}`, form).then(() => undefined);
};

export const deleteVerificationDocument = (docType: VerificationDocType): Promise<void> =>
  api.delete(`/api/association/verification/documents/${docType}`).then(() => undefined);

export const submitVerification = (): Promise<void> =>
  api.post('/api/association/verification/submit').then(() => undefined);

export const listOptionalDocuments = (): Promise<OptionalDocumentDto[]> =>
  api.get<OptionalDocumentDto[]>('/api/association/documents').then((r) => r.data);

export const uploadOptionalDocument = (
  file: File,
  category: string,
): Promise<OptionalDocumentDto> => {
  const form = new FormData();
  form.append('file', file);
  form.append('category', category);
  return api.post<OptionalDocumentDto>('/api/association/documents', form).then((r) => r.data);
};

export const deleteOptionalDocument = (id: string): Promise<void> =>
  api.delete(`/api/association/documents/${id}`).then(() => undefined);

export const downloadDocument = async (id: string, fileName: string): Promise<void> => {
  const response = await api.get(`/api/association/documents/${id}/content`, {
    responseType: 'blob',
  });
  const url = URL.createObjectURL(response.data as Blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
};

export const downloadOptionalDocument = downloadDocument;
