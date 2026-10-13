/** Shape of a donation public ref (`Donation.publicRef`, a UUID minted by the backend). */
const DONATION_REF_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Validates the `clRef` query parameter that a CommonLink loader passes to its iframe after a
 * confirmed payment. Returns the ref when it is a well-formed UUID, null otherwise — the value
 * comes from the host page URL and ends up in an API path.
 */
export function resolveDonationRef(raw: string | undefined | null): string | null {
  if (!raw) return null;
  return DONATION_REF_PATTERN.test(raw) ? raw.toLowerCase() : null;
}
