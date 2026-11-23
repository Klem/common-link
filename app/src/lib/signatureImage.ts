import { SIGNATORY_LIMITS, SIGNATURE_IMAGE_TYPES } from '@/types/signatory';

/**
 * Box every signature image is fitted into before upload. Smaller than the backend bounds
 * (1200×600) on purpose: it keeps the PNG well under the 200 KB limit and matches the
 * aspect ratio of the signature frame printed on the receipt.
 */
export const SIGNATURE_BOX = { WIDTH: 600, HEIGHT: 200 } as const;

/** Reasons a signature image can be refused client-side. */
export const SignatureImageErrorCode = {
  UNSUPPORTED_TYPE: 'UNSUPPORTED_TYPE',
  UNREADABLE: 'UNREADABLE',
  TOO_LARGE: 'TOO_LARGE',
} as const;
export type SignatureImageErrorCode = (typeof SignatureImageErrorCode)[keyof typeof SignatureImageErrorCode];

/** Thrown by the helpers below; `code` maps to an i18n message in the signatory card. */
export class SignatureImageError extends Error {
  constructor(readonly code: SignatureImageErrorCode) {
    super(code);
    this.name = 'SignatureImageError';
  }
}

/**
 * Computes the size of an image fitted into a box, keeping its ratio. Never upscales.
 *
 * @return integer dimensions, at least 1×1.
 */
export function fitToBox(
  width: number,
  height: number,
  maxWidth: number = SIGNATURE_BOX.WIDTH,
  maxHeight: number = SIGNATURE_BOX.HEIGHT,
): { width: number; height: number } {
  const scale = Math.min(1, maxWidth / width, maxHeight / height);
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/** Promise wrapper of `canvas.toBlob`; a null blob (encoding failure) rejects as UNREADABLE. */
function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new SignatureImageError(SignatureImageErrorCode.UNREADABLE))),
      type,
      quality,
    );
  });
}

/**
 * Re-encodes a drawable source into a signature image fitted into {@link SIGNATURE_BOX}.
 * The PNG keeps transparency. When it exceeds the backend size limit and `allowJpegFallback`
 * is set (opaque sources such as JPEG photos), a JPEG is tried before giving up.
 *
 * @param source pad canvas or decoded image
 * @param width intrinsic width of the source, in pixels
 * @param height intrinsic height of the source, in pixels
 * @throws SignatureImageError TOO_LARGE when no encoding fits under 204 800 bytes
 */
export async function toSignatureBlob(
  source: CanvasImageSource,
  width: number,
  height: number,
  allowJpegFallback = false,
): Promise<Blob> {
  const size = fitToBox(width, height);
  const canvas = document.createElement('canvas');
  canvas.width = size.width;
  canvas.height = size.height;
  const context = canvas.getContext('2d');
  if (!context) throw new SignatureImageError(SignatureImageErrorCode.UNREADABLE);
  context.drawImage(source, 0, 0, size.width, size.height);

  const png = await canvasToBlob(canvas, 'image/png');
  if (png.size <= SIGNATORY_LIMITS.IMAGE_MAX_BYTES) return png;
  if (allowJpegFallback) {
    const jpeg = await canvasToBlob(canvas, 'image/jpeg', 0.85);
    if (jpeg.size <= SIGNATORY_LIMITS.IMAGE_MAX_BYTES) return jpeg;
  }
  throw new SignatureImageError(SignatureImageErrorCode.TOO_LARGE);
}

/** Decodes an image file through a temporary object URL; a decoding failure rejects as UNREADABLE. */
function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new SignatureImageError(SignatureImageErrorCode.UNREADABLE));
    };
    image.src = url;
  });
}

/**
 * Validates and re-encodes an uploaded signature file (mirror of the backend checks:
 * PNG/JPEG only, ≤ 1200×600, ≤ 204 800 bytes). The original file is never sent as is.
 *
 * @throws SignatureImageError UNSUPPORTED_TYPE, UNREADABLE or TOO_LARGE
 */
export async function processSignatureFile(file: File): Promise<Blob> {
  if (!SIGNATURE_IMAGE_TYPES.includes(file.type)) {
    throw new SignatureImageError(SignatureImageErrorCode.UNSUPPORTED_TYPE);
  }
  const image = await loadImage(file);
  return toSignatureBlob(image, image.naturalWidth, image.naturalHeight, file.type === 'image/jpeg');
}
