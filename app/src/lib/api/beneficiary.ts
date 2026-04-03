import api from '@/lib/api';
import type {
  BeneficiaryDto,
  CreateBeneficiaryRequest,
  AddIbanRequest,
  VopVerifyResponseDto,
  SireneSearchResultDto,
} from '@/types/beneficiary';

/**
 * Fetches the list of beneficiaries for the current association.
 * Calls `GET /api/association/beneficiaries`.
 *
 * @returns Array of beneficiary DTOs, each including their IBANs.
 */
export const getBeneficiaries = (): Promise<BeneficiaryDto[]> =>
  api.get<BeneficiaryDto[]>('/api/association/beneficiaries').then((r) => r.data);

/**
 * Creates a new beneficiary for the current association.
 * Calls `POST /api/association/beneficiaries`.
 *
 * @param data - Beneficiary creation payload.
 * @returns The newly created beneficiary DTO.
 */
export const createBeneficiary = (data: CreateBeneficiaryRequest): Promise<BeneficiaryDto> =>
  api.post<BeneficiaryDto>('/api/association/beneficiaries', data).then((r) => r.data);

/**
 * Deletes a beneficiary by ID.
 * Calls `DELETE /api/association/beneficiaries/:id`.
 *
 * @param id - UUID of the beneficiary to delete.
 */
export const deleteBeneficiary = (id: string): Promise<void> =>
  api.delete(`/api/association/beneficiaries/${id}`);

/**
 * Adds an IBAN to a beneficiary.
 * Calls `POST /api/association/beneficiaries/:beneficiaryId/ibans`.
 *
 * @param beneficiaryId - UUID of the beneficiary.
 * @param data - IBAN payload.
 * @returns The updated beneficiary DTO (with the new IBAN included).
 */
export const addIban = (beneficiaryId: string, data: AddIbanRequest): Promise<BeneficiaryDto> =>
  api
    .post<BeneficiaryDto>(`/api/association/beneficiaries/${beneficiaryId}/ibans`, data)
    .then((r) => r.data);

/**
 * Removes an IBAN from a beneficiary.
 * Calls `DELETE /api/association/beneficiaries/:beneficiaryId/ibans/:ibanId`.
 *
 * @param beneficiaryId - UUID of the beneficiary.
 * @param ibanId - UUID of the IBAN to remove.
 */
export const deleteIban = (beneficiaryId: string, ibanId: string): Promise<void> =>
  api.delete(`/api/association/beneficiaries/${beneficiaryId}/ibans/${ibanId}`);

/**
 * Triggers a Verification of Payee (VOP) check for a beneficiary IBAN.
 * Calls `POST /api/association/beneficiaries/:beneficiaryId/ibans/:ibanId/verify-vop`.
 * The IBAN must have status FORMAT_VALID before this can be called.
 *
 * @param beneficiaryId - UUID of the beneficiary.
 * @param ibanId - UUID of the IBAN to verify.
 * @returns VOP verification result including the updated status and optional suggested name.
 */
export const verifyIbanVop = (
  beneficiaryId: string,
  ibanId: string,
): Promise<VopVerifyResponseDto> =>
  api
    .post<VopVerifyResponseDto>(
      `/api/association/beneficiaries/${beneficiaryId}/ibans/${ibanId}/verify-vop`,
    )
    .then((r) => r.data);

/**
 * Searches the INSEE Sirene directory by SIREN (9 digits) or SIRET (14 digits).
 * Calls `GET /api/association/sirene/search?q={query}`.
 *
 * @param query - A 9-digit SIREN or 14-digit SIRET number.
 * @returns Simplified Sirene search result with key entity fields.
 */
export const searchSirene = (query: string): Promise<SireneSearchResultDto> =>
  api
    .get<SireneSearchResultDto>('/api/association/sirene/search', { params: { q: query } })
    .then((r) => r.data);
