import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getSignatoryImage, saveSignatory } from '../signatory';

const { mockGet, mockPut } = vi.hoisted(() => ({ mockGet: vi.fn(), mockPut: vi.fn() }));
vi.mock('@/lib/api', () => ({ default: { get: mockGet, put: mockPut } }));

const dto = {
  signerName: 'Marie Dupont',
  signerRole: 'Présidente',
  mode: 'DRAWN',
  hasSignature: true,
  attestedAt: '2026-11-23T10:00:00Z',
  updatedAt: '2026-11-23T10:00:00Z',
};

beforeEach(() => {
  mockGet.mockReset();
  mockPut.mockReset().mockResolvedValue({ data: dto });
});

describe('saveSignatory', () => {
  it('sends the multipart parts of the contract, with the attestation', async () => {
    const file = new Blob(['png'], { type: 'image/png' });
    await expect(saveSignatory({ signerName: 'Marie Dupont', signerRole: 'Présidente', mode: 'DRAWN', file })).resolves.toEqual(dto);

    const [url, form] = mockPut.mock.calls[0] as [string, FormData];
    expect(url).toBe('/api/association/me/signatory');
    expect(form.get('signerName')).toBe('Marie Dupont');
    expect(form.get('signerRole')).toBe('Présidente');
    expect(form.get('mode')).toBe('DRAWN');
    expect(form.get('attested')).toBe('true');
    const part = form.get('file') as File;
    expect(part).toBeInstanceOf(Blob);
    expect(part.name).toBe('signature.png');
    // No explicit Content-Type: the browser sets the multipart boundary.
    expect(mockPut.mock.calls[0]).toHaveLength(2);
  });

  it('names a JPEG part signature.jpg', async () => {
    await saveSignatory({
      signerName: 'Marie Dupont',
      signerRole: 'Présidente',
      mode: 'UPLOAD',
      file: new Blob(['jpg'], { type: 'image/jpeg' }),
    });
    const form = mockPut.mock.calls[0][1] as FormData;
    expect((form.get('file') as File).name).toBe('signature.jpg');
    expect(form.get('mode')).toBe('UPLOAD');
  });
});

describe('getSignatoryImage', () => {
  it('reads the image as a blob', async () => {
    const blob = new Blob(['img'], { type: 'image/png' });
    mockGet.mockResolvedValue({ data: blob });
    await expect(getSignatoryImage()).resolves.toBe(blob);
    expect(mockGet).toHaveBeenCalledWith('/api/association/me/signatory/image', { responseType: 'blob' });
  });

  it('maps a 404 (no signature saved) to null and rethrows other errors', async () => {
    mockGet.mockRejectedValueOnce({ response: { status: 404 } });
    await expect(getSignatoryImage()).resolves.toBeNull();
    mockGet.mockRejectedValueOnce({ response: { status: 500 } });
    await expect(getSignatoryImage()).rejects.toEqual({ response: { status: 500 } });
  });
});
