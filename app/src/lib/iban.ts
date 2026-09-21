/**
 * Client-side IBAN format check — mirrors `PayeeService.isValidIban` on the backend byte-for-byte
 * (same regex, same mod-97 algorithm) so a format error can be shown inline before the round-trip
 * to `POST /api/association/payees/:id/ibans`, which re-validates independently (every click is
 * replayable).
 */

const IBAN_STRUCTURE = /^[A-Z]{2}[0-9]{2}[A-Z0-9]{10,30}$/;

/** Uppercases and strips whitespace, matching the backend's normalisation before validation/storage. */
export function normalizeIban(raw: string): string {
  return raw.toUpperCase().replace(/\s/g, '');
}

/**
 * Validates a normalised IBAN using the ISO 13616 mod-97 algorithm.
 *
 * @param raw The raw IBAN input (any casing/spacing — normalised internally).
 * @returns `true` if the IBAN passes the mod-97 checksum, `false` otherwise.
 */
export function isValidIbanFormat(raw: string): boolean {
  const iban = normalizeIban(raw);
  if (!IBAN_STRUCTURE.test(iban)) return false;

  const rearranged = iban.slice(4) + iban.slice(0, 4);
  const numeric = rearranged
    .split('')
    .map((c) => (/[A-Z]/.test(c) ? (c.charCodeAt(0) - 65 + 10).toString() : c))
    .join('');

  return BigInt(numeric) % BigInt(97) === BigInt(1);
}
