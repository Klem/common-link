import api from '@/lib/api';
import type { SaveSignatoryRequest, SignatoryDto } from '@/types/signatory';

const BASE = '/api/association/me/signatory';

/** Reads the authorised signer of the authenticated association. */
export const getSignatory = (): Promise<SignatoryDto> => api.get<SignatoryDto>(BASE).then((r) => r.data);

/**
 * Downloads the saved signature image.
 *
 * @returns the image bytes, or null when the API answers 404 (no signature saved yet).
 */
export const getSignatoryImage = async (): Promise<Blob | null> => {
  try {
    const response = await api.get<Blob>(`${BASE}/image`, { responseType: 'blob' });
    return response.data;
  } catch (error) {
    if ((error as { response?: { status?: number } }).response?.status === 404) return null;
    throw error;
  }
};

/**
 * Saves the authorised signer and their signature image (always sent: every mode is an image).
 * The attestation is always sent as `true`: the UI only enables the save once the attestation
 * checkbox is ticked (the API answers 422 otherwise).
 */
export const saveSignatory = (request: SaveSignatoryRequest): Promise<SignatoryDto> => {
  const form = new FormData();
  form.append('signerName', request.signerName);
  form.append('signerRole', request.signerRole);
  form.append('mode', request.mode);
  form.append('attested', 'true');
  form.append('file', request.file, request.file.type === 'image/jpeg' ? 'signature.jpg' : 'signature.png');
  return api.put<SignatoryDto>(BASE, form).then((r) => r.data);
};
