import api from '@/lib/api';
import type {
  PayeeDto,
  CreatePayeeRequest,
  AddIbanRequest,
  VopVerifyResponseDto,
  SireneSearchResultDto,
} from '@/types/payee';

/**
 * Fetches the list of payees for the current association.
 * Calls `GET /api/association/payees`.
 *
 * @returns Array of payee DTOs, each including their IBANs.
 */
export const getPayees = (): Promise<PayeeDto[]> =>
  api.get<PayeeDto[]>('/api/association/payees').then((r) => r.data);

/**
 * Creates a new payee for the current association.
 * Calls `POST /api/association/payees`.
 *
 * @param data - Payee creation payload.
 * @returns The newly created payee DTO.
 */
export const createPayee = (data: CreatePayeeRequest): Promise<PayeeDto> =>
  api.post<PayeeDto>('/api/association/payees', data).then((r) => r.data);

/**
 * Deletes a payee by ID.
 * Calls `DELETE /api/association/payees/:id`.
 *
 * @param id - UUID of the payee to delete.
 */
export const deletePayee = (id: string): Promise<void> =>
  api.delete(`/api/association/payees/${id}`);

/**
 * Adds an IBAN to a payee.
 * Calls `POST /api/association/payees/:payeeId/ibans`.
 *
 * @param payeeId - UUID of the payee.
 * @param data - IBAN payload.
 * @returns The updated payee DTO (with the new IBAN included).
 */
export const addIban = (payeeId: string, data: AddIbanRequest): Promise<PayeeDto> =>
  api
    .post<PayeeDto>(`/api/association/payees/${payeeId}/ibans`, data)
    .then((r) => r.data);

/**
 * Removes an IBAN from a payee.
 * Calls `DELETE /api/association/payees/:payeeId/ibans/:ibanId`.
 *
 * @param payeeId - UUID of the payee.
 * @param ibanId - UUID of the IBAN to remove.
 */
export const deleteIban = (payeeId: string, ibanId: string): Promise<void> =>
  api.delete(`/api/association/payees/${payeeId}/ibans/${ibanId}`);

/**
 * Triggers a Verification of Payee (VOP) check for a payee IBAN.
 * Calls `POST /api/association/payees/:payeeId/ibans/:ibanId/verify-vop`.
 * The IBAN must have status FORMAT_VALID before this can be called.
 *
 * @param payeeId - UUID of the payee.
 * @param ibanId - UUID of the IBAN to verify.
 * @returns VOP verification result including the updated status and optional suggested name.
 */
export const verifyIbanVop = (
  payeeId: string,
  ibanId: string,
): Promise<VopVerifyResponseDto> =>
  api
    .post<VopVerifyResponseDto>(
      `/api/association/payees/${payeeId}/ibans/${ibanId}/verify-vop`,
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
