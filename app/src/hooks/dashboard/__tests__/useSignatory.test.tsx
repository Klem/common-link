import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useSignatory } from '@/hooks/dashboard/useSignatory';
import { getSignatory, getSignatoryImage, saveSignatory } from '@/lib/api/signatory';
import type { SignatoryDto } from '@/types/signatory';

vi.mock('@/lib/api/signatory', () => ({
  getSignatory: vi.fn(),
  getSignatoryImage: vi.fn(),
  saveSignatory: vi.fn(),
}));

const { mockAddToast } = vi.hoisted(() => ({ mockAddToast: vi.fn() }));
vi.mock('@/stores/toastStore', () => ({
  useToastStore: () => ({ addToast: mockAddToast }),
}));

const base: SignatoryDto = {
  signerName: 'Marie Dupont',
  signerRole: 'Présidente',
  mode: null,
  hasSignature: false,
  attestedAt: null,
  updatedAt: null,
};

beforeEach(() => {
  vi.mocked(getSignatory).mockReset();
  vi.mocked(getSignatoryImage).mockReset();
  vi.mocked(saveSignatory).mockReset();
  mockAddToast.mockReset();
  URL.createObjectURL = vi.fn(() => 'blob:url');
  URL.revokeObjectURL = vi.fn();
});

describe('useSignatory', () => {
  it('fetches the image for an image mode and exposes its object URL', async () => {
    const image = new Blob(['x'], { type: 'image/png' });
    vi.mocked(getSignatory).mockResolvedValue({ ...base, mode: 'UPLOAD', hasSignature: true });
    vi.mocked(getSignatoryImage).mockResolvedValue(image);
    const { result } = renderHook(() => useSignatory());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.savedImage).toBe(image);
    expect(result.current.savedImageUrl).toBe('blob:url');
  });

  it('does not fetch any image while no signature is saved', async () => {
    vi.mocked(getSignatory).mockResolvedValue(base);
    const { result } = renderHook(() => useSignatory());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(getSignatoryImage).not.toHaveBeenCalled();
    expect(result.current.savedImageUrl).toBeNull();
  });

  it('save stores the new state, keeps the sent image and toasts', async () => {
    vi.mocked(getSignatory).mockResolvedValue(base);
    const saved = { ...base, mode: 'DRAWN' as const, hasSignature: true, updatedAt: '2026-11-23T10:00:00Z' };
    vi.mocked(saveSignatory).mockResolvedValue(saved);
    const { result } = renderHook(() => useSignatory());
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    const file = new Blob(['png'], { type: 'image/png' });
    let ok = false;
    await act(async () => {
      ok = await result.current.save({ signerName: 'Marie Dupont', signerRole: 'Présidente', mode: 'DRAWN', file });
    });
    expect(ok).toBe(true);
    expect(result.current.signatory).toEqual(saved);
    expect(result.current.savedImage).toBe(file);
    expect(mockAddToast).toHaveBeenCalledWith('success', 'signatorySaved');
  });

  it('save resolves false when the API refuses it', async () => {
    vi.mocked(getSignatory).mockResolvedValue(base);
    vi.mocked(saveSignatory).mockRejectedValue({ response: { status: 422 } });
    const { result } = renderHook(() => useSignatory());
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    let ok = true;
    await act(async () => {
      ok = await result.current.save({
        signerName: 'A',
        signerRole: 'B',
        mode: 'UPLOAD',
        file: new Blob(['png'], { type: 'image/png' }),
      });
    });
    expect(ok).toBe(false);
    expect(result.current.signatory).toEqual(base);
  });
});
