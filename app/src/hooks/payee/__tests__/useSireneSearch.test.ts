import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSireneSearch } from '../useSireneSearch';

vi.mock('@/lib/api/payee', () => ({
  searchSirene: vi.fn(),
}));

import { searchSirene } from '@/lib/api/payee';

const mockSearchSirene = searchSirene as ReturnType<typeof vi.fn>;

const sampleResult = {
  siren: '775671356',
  siret: null,
  name: 'Les Restos du Coeur',
  nafCode: '8899B',
  category: null,
  city: 'Paris',
  postalCode: '75001',
  address: null,
  active: true,
  isSiege: true,
  isEss: false,
  creationDate: '1985-06-27',
  employeeRange: null,
};

describe('useSireneSearch', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── detectedType ──────────────────────────────────────────────────────────

  it('detects siren for exactly 9 digits', () => {
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('775671356');
    });

    expect(result.current.detectedType).toBe('siren');
  });

  it('detects siret for exactly 14 digits', () => {
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('77567135600012');
    });

    expect(result.current.detectedType).toBe('siret');
  });

  it('returns null for partial input', () => {
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('12345');
    });

    expect(result.current.detectedType).toBeNull();
  });

  it('returns null for empty query', () => {
    const { result } = renderHook(() => useSireneSearch());

    expect(result.current.detectedType).toBeNull();
  });

  // ── search ────────────────────────────────────────────────────────────────

  it('calls searchSirene and updates result on success', async () => {
    mockSearchSirene.mockResolvedValue(sampleResult);
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('775671356');
    });

    await act(async () => {
      await result.current.search();
    });

    expect(mockSearchSirene).toHaveBeenCalledWith('775671356');
    expect(result.current.result).toEqual(sampleResult);
    expect(result.current.error).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });

  it('does not call searchSirene when detectedType is null', async () => {
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('12345');
    });

    await act(async () => {
      await result.current.search();
    });

    expect(mockSearchSirene).not.toHaveBeenCalled();
    expect(result.current.result).toBeNull();
  });

  it('sets error key on 404 response', async () => {
    mockSearchSirene.mockRejectedValue({ response: { status: 404 } });
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('775671356');
    });

    await act(async () => {
      await result.current.search();
    });

    expect(result.current.error).toBe('payees.search.errors.404');
    expect(result.current.result).toBeNull();
  });

  it('sets error key on 400 response', async () => {
    mockSearchSirene.mockRejectedValue({ response: { status: 400 } });
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('775671356');
    });

    await act(async () => {
      await result.current.search();
    });

    expect(result.current.error).toBe('payees.search.errors.400');
  });

  it('sets network error key when no response status', async () => {
    mockSearchSirene.mockRejectedValue(new Error('Network Error'));
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('775671356');
    });

    await act(async () => {
      await result.current.search();
    });

    expect(result.current.error).toBe('payees.search.errors.network');
  });

  // ── clear ─────────────────────────────────────────────────────────────────

  it('clear resets result and error without changing query', async () => {
    mockSearchSirene.mockResolvedValue(sampleResult);
    const { result } = renderHook(() => useSireneSearch());

    act(() => {
      result.current.setQuery('775671356');
    });
    await act(async () => {
      await result.current.search();
    });

    act(() => {
      result.current.clear();
    });

    expect(result.current.result).toBeNull();
    expect(result.current.error).toBeNull();
    expect(result.current.query).toBe('775671356');
  });
});
