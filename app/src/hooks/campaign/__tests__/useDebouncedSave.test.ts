import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useDebouncedCallback, useDebouncedPatchSave } from '../useDebouncedSave';

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useDebouncedCallback', () => {
  it('calls fn after the delay, not immediately', () => {
    const fn = vi.fn();
    const { result } = renderHook(() => useDebouncedCallback(fn, 800));

    act(() => { result.current.schedule(); });
    expect(fn).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(800); });
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('resets the timer on repeated schedule calls', () => {
    const fn = vi.fn();
    const { result } = renderHook(() => useDebouncedCallback(fn, 800));

    act(() => { result.current.schedule(); });
    act(() => { vi.advanceTimersByTime(500); });
    act(() => { result.current.schedule(); });
    act(() => { vi.advanceTimersByTime(500); });
    expect(fn).not.toHaveBeenCalled();

    act(() => { vi.advanceTimersByTime(300); });
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('cancel() prevents the pending call from firing', () => {
    const fn = vi.fn();
    const { result } = renderHook(() => useDebouncedCallback(fn, 800));

    act(() => { result.current.schedule(); });
    act(() => { result.current.cancel(); });
    act(() => { vi.advanceTimersByTime(800); });
    expect(fn).not.toHaveBeenCalled();
  });

  it('flush() calls fn immediately and prevents a duplicate call later', () => {
    const fn = vi.fn();
    const { result } = renderHook(() => useDebouncedCallback(fn, 800));

    act(() => { result.current.schedule(); });
    act(() => { result.current.flush(); });
    expect(fn).toHaveBeenCalledTimes(1);

    act(() => { vi.advanceTimersByTime(800); });
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('flush() is a no-op when nothing is pending', () => {
    const fn = vi.fn();
    const { result } = renderHook(() => useDebouncedCallback(fn, 800));

    act(() => { result.current.flush(); });
    expect(fn).not.toHaveBeenCalled();
  });

  it('flushes a pending call on unmount (e.g. tab switch before the delay elapses)', () => {
    const fn = vi.fn();
    const { result, unmount } = renderHook(() => useDebouncedCallback(fn, 800));

    act(() => { result.current.schedule(); });
    unmount();
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('does not call fn on unmount when nothing is pending', () => {
    const fn = vi.fn();
    const { unmount } = renderHook(() => useDebouncedCallback(fn, 800));

    unmount();
    expect(fn).not.toHaveBeenCalled();
  });
});

describe('useDebouncedPatchSave', () => {
  it('merges successive patches into a single save call', () => {
    const save = vi.fn();
    const { result } = renderHook(() => useDebouncedPatchSave<{ a?: string; b?: string }>(save, 800));

    act(() => { result.current.schedule({ a: '1' }); });
    act(() => { vi.advanceTimersByTime(300); });
    act(() => { result.current.schedule({ b: '2' }); });
    act(() => { vi.advanceTimersByTime(800); });

    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith({ a: '1', b: '2' });
  });

  it('cancel() discards the pending patch entirely', () => {
    const save = vi.fn();
    const { result } = renderHook(() => useDebouncedPatchSave<{ a?: string }>(save, 800));

    act(() => { result.current.schedule({ a: '1' }); });
    act(() => { result.current.cancel(); });
    act(() => { vi.advanceTimersByTime(800); });
    expect(save).not.toHaveBeenCalled();
  });

  it('flush() sends the merged patch immediately', () => {
    const save = vi.fn();
    const { result } = renderHook(() => useDebouncedPatchSave<{ a?: string }>(save, 800));

    act(() => { result.current.schedule({ a: '1' }); });
    act(() => { result.current.flush(); });
    expect(save).toHaveBeenCalledWith({ a: '1' });
  });

  it('flushes the pending patch on unmount', () => {
    const save = vi.fn();
    const { result, unmount } = renderHook(() => useDebouncedPatchSave<{ a?: string }>(save, 800));

    act(() => { result.current.schedule({ a: '1' }); });
    unmount();
    expect(save).toHaveBeenCalledWith({ a: '1' });
  });

  it('always calls the latest save callback, not a stale one', () => {
    const save1 = vi.fn();
    const save2 = vi.fn();
    const { result, rerender } = renderHook(
      ({ save }) => useDebouncedPatchSave<{ a?: string }>(save, 800),
      { initialProps: { save: save1 } },
    );

    act(() => { result.current.schedule({ a: '1' }); });
    rerender({ save: save2 });
    act(() => { vi.advanceTimersByTime(800); });

    expect(save1).not.toHaveBeenCalled();
    expect(save2).toHaveBeenCalledWith({ a: '1' });
  });
});
