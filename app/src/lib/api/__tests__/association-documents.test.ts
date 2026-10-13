import { describe, it, expect, vi, beforeEach } from 'vitest';
import { downloadOwnAssociationDocument, fileNameFromDisposition } from '../association-documents';

const { mockGet } = vi.hoisted(() => ({ mockGet: vi.fn() }));
vi.mock('@/lib/api', () => ({ default: { get: mockGet } }));

describe('fileNameFromDisposition', () => {
  it('reads the RFC 5987 name first, then the plain one', () => {
    expect(fileNameFromDisposition("attachment; filename=\"statuts.pdf\"; filename*=UTF-8''statuts%20sign%C3%A9s.pdf")).toBe(
      'statuts signés.pdf',
    );
    expect(fileNameFromDisposition('attachment; filename="recepisse.pdf"')).toBe('recepisse.pdf');
    expect(fileNameFromDisposition('')).toBeNull();
  });

  it('decodes the UTF-8 name Spring sends for non-Latin-1 characters (R11)', () => {
    const header =
      "attachment; filename=\"=?UTF-8?Q?Proc=C3=A8s-verbal_=C5=93uvre.pdf?=\"; filename*=UTF-8''Proc%C3%A8s-verbal%20%C5%93uvre.pdf";
    expect(fileNameFromDisposition(header)).toBe('Procès-verbal œuvre.pdf');
  });
});

describe('downloadOwnAssociationDocument', () => {
  let clicked: string[];

  beforeEach(() => {
    clicked = [];
    mockGet.mockReset();
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this.download);
    });
  });

  it('calls the ownership-checked content endpoint and keeps the server file name when readable', async () => {
    mockGet.mockResolvedValue({
      data: new Blob(['x'], { type: 'application/pdf' }),
      headers: { 'content-disposition': 'attachment; filename="statuts.pdf"' },
    });

    await downloadOwnAssociationDocument('doc-1', 'commonlink-statutes');

    expect(mockGet).toHaveBeenCalledWith('/api/association/documents/doc-1/content', { responseType: 'blob' });
    expect(clicked).toEqual(['statuts.pdf']);
  });

  it('falls back to the given name plus the content-type extension when the header is not exposed', async () => {
    mockGet.mockResolvedValue({ data: new Blob(['x'], { type: 'image/png' }), headers: {} });

    await downloadOwnAssociationDocument('doc-2', 'commonlink-proof');

    expect(clicked).toEqual(['commonlink-proof.png']);
  });
});
