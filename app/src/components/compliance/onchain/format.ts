/**
 * Formats an ISO instant for the « Blockchain » compliance section (date and time, locale-aware).
 *
 * @returns `—` when [iso] is null.
 */
export function formatOnchainDateTime(iso: string | null, locale: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(locale === 'fr' ? 'fr-FR' : 'en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

/** Extracts the RFC-7807 `detail` of a failed request (the backend reasons are already in French), or null. */
export function problemDetail(err: unknown): string | null {
  if (!err || typeof err !== 'object' || !('response' in err)) return null;
  const data = (err as { response?: { data?: unknown } }).response?.data;
  if (!data || typeof data !== 'object' || !('detail' in data)) return null;
  const detail = (data as { detail?: unknown }).detail;
  return typeof detail === 'string' && detail.trim() ? detail : null;
}
