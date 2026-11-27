import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  fitToBox,
  processSignatureFile,
  SignatureImageError,
  SignatureImageErrorCode,
  toSignatureBlob,
} from '@/lib/signatureImage';

describe('fitToBox', () => {
  it('downscales into 600×200 keeping the ratio', () => {
    expect(fitToBox(1200, 200)).toEqual({ width: 600, height: 100 });
    expect(fitToBox(300, 600)).toEqual({ width: 100, height: 200 });
  });

  it('never upscales a small image', () => {
    expect(fitToBox(150, 50)).toEqual({ width: 150, height: 50 });
  });
});

describe('toSignatureBlob', () => {
  /** Sizes returned by successive `toBlob` calls, by requested MIME type. */
  let blobSizes: Record<string, number>;
  let drawn: number[][];

  beforeEach(() => {
    blobSizes = { 'image/png': 1000, 'image/jpeg': 1000 };
    drawn = [];
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: (_s: unknown, x: number, y: number, w: number, h: number) => drawn.push([x, y, w, h]),
    } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (
      this: HTMLCanvasElement,
      callback: BlobCallback,
      type?: string,
    ) {
      const mime = type ?? 'image/png';
      callback(new Blob([new Uint8Array(blobSizes[mime])], { type: mime }));
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('draws the source fitted into the signature box and returns a PNG', async () => {
    const blob = await toSignatureBlob(document.createElement('canvas'), 2400, 600);
    expect(drawn).toEqual([[0, 0, 600, 150]]);
    expect(blob.type).toBe('image/png');
  });

  it('refuses a result above 204 800 bytes', async () => {
    blobSizes['image/png'] = 204_801;
    await expect(toSignatureBlob(document.createElement('canvas'), 600, 200)).rejects.toMatchObject({
      code: SignatureImageErrorCode.TOO_LARGE,
    });
  });

  it('falls back to JPEG for opaque sources when the PNG is too large', async () => {
    blobSizes['image/png'] = 300_000;
    const blob = await toSignatureBlob(document.createElement('canvas'), 600, 200, true);
    expect(blob.type).toBe('image/jpeg');
  });
});

describe('processSignatureFile', () => {
  it.each(['image/svg+xml', 'image/gif', 'application/pdf'])('refuses %s before decoding it', async (type) => {
    const error = await processSignatureFile(new File(['x'], 'sig', { type })).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(SignatureImageError);
    expect((error as SignatureImageError).code).toBe(SignatureImageErrorCode.UNSUPPORTED_TYPE);
  });
});
