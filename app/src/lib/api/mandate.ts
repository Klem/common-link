import api from '@/lib/api';
import type { MandateStateDto, MandateDocType, SignMandateRequest } from '@/types/mandate';

export const getMandateState = (): Promise<MandateStateDto> =>
  api.get<MandateStateDto>('/api/association/mandate').then((r) => r.data);

export const uploadMandateDocument = (docType: MandateDocType, file: File): Promise<void> => {
  const form = new FormData();
  form.append('file', file);
  return api.put(`/api/association/mandate/documents/${docType}`, form).then(() => undefined);
};

export const deleteMandateDocument = (docType: MandateDocType): Promise<void> =>
  api.delete(`/api/association/mandate/documents/${docType}`).then(() => undefined);

export const signMandate = (request: SignMandateRequest): Promise<MandateStateDto> =>
  api.post<MandateStateDto>('/api/association/mandate/sign', request).then((r) => r.data);

export const revokeMandate = (): Promise<void> =>
  api.post('/api/association/mandate/revoke').then(() => undefined);

export const downloadMandatePdf = async (): Promise<void> => {
  const response = await api.get('/api/association/mandate/pdf', { responseType: 'blob' });
  const url = URL.createObjectURL(response.data as Blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'mandat-fiscal.pdf';
  a.click();
  URL.revokeObjectURL(url);
};
