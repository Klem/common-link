import api from '@/lib/api';

/** File extension per content type, used when the server-provided file name cannot be read. */
const EXTENSION_BY_CONTENT_TYPE: Record<string, string> = {
  'application/pdf': 'pdf',
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
};

/**
 * Extracts the file name from a `Content-Disposition` header, RFC 5987 `filename*` first.
 *
 * @param disposition raw header value, possibly empty.
 * @return the decoded file name, or null when the header carries none.
 */
export function fileNameFromDisposition(disposition: string): string | null {
  const extended = disposition.match(/filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/);
  if (extended) {
    try {
      return decodeURIComponent(extended[1].trim());
    } catch {
      // Malformed percent-encoding: fall through to the plain parameter.
    }
  }
  const plain = disposition.match(/filename\s*=\s*("([^"]*)"|[^;]+)/);
  if (!plain) return null;
  const name = (plain[2] ?? plain[1]).trim();
  return name || null;
}

/**
 * Downloads one of the association's own KYB documents
 * (`GET /api/association/documents/{id}/content`, ownership-checked and journalled server-side).
 *
 * The server sends the original file name in `Content-Disposition`, but the API is cross-origin and
 * only exposes `Authorization` to scripts (CORS `exposedHeaders`), so the header is usually
 * unreadable from the browser. In that case the file is saved as `fallbackBaseName` plus an
 * extension derived from the response content type.
 *
 * Unlike `downloadDocument` in `lib/api/verification.ts`, this function does not require the caller
 * to know the file name: the onboarding context deliberately exposes only document ids and types.
 *
 * @param id document id, as exposed by the onboarding context.
 * @param fallbackBaseName file name to use, without extension, when the server name is unreadable.
 */
export async function downloadOwnAssociationDocument(id: string, fallbackBaseName: string): Promise<void> {
  const response = await api.get<Blob>(`/api/association/documents/${id}/content`, {
    responseType: 'blob',
  });
  const blob = response.data;
  const disposition = String(response.headers?.['content-disposition'] ?? '');
  const extension = EXTENSION_BY_CONTENT_TYPE[blob.type?.split(';')[0].trim() ?? ''];
  const fileName =
    fileNameFromDisposition(disposition) ?? (extension ? `${fallbackBaseName}.${extension}` : fallbackBaseName);

  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}
