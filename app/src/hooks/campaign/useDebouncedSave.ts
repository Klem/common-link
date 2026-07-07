'use client';

import { useCallback, useEffect, useRef } from 'react';

/**
 * Debounces calls to `fn`, flushing (calling `fn` immediately) on unmount so a
 * pending save is never lost when the component is torn down (e.g. tab switch).
 */
export function useDebouncedCallback(fn: () => void, delay = 800): { schedule: () => void; cancel: () => void; flush: () => void } {
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pending = useRef(false);

  const cancel = useCallback(() => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    pending.current = false;
  }, []);

  const flush = useCallback(() => {
    const hadPending = pending.current;
    cancel();
    if (hadPending) fnRef.current();
  }, [cancel]);

  const schedule = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    pending.current = true;
    timer.current = setTimeout(flush, delay);
  }, [delay, flush]);

  useEffect(() => () => flush(), [flush]);

  return { schedule, cancel, flush };
}

/**
 * Debounces partial saves of type `T`, merging successive patches so a field
 * edited just before another field retriggers the timer is never dropped.
 * Flushes (saves the merged patch immediately) on unmount.
 */
export function useDebouncedPatchSave<T extends object>(
  save: (patch: T) => void,
  delay = 800,
): { schedule: (patch: Partial<T>) => void; cancel: () => void; flush: () => void } {
  const saveRef = useRef(save);
  saveRef.current = save;
  const pendingPatch = useRef<Partial<T> | null>(null);

  const { schedule: scheduleFlush, cancel: cancelInner, flush } = useDebouncedCallback(() => {
    const patch = pendingPatch.current;
    pendingPatch.current = null;
    if (patch) saveRef.current(patch as T);
  }, delay);

  const cancel = useCallback(() => {
    pendingPatch.current = null;
    cancelInner();
  }, [cancelInner]);

  const schedule = useCallback(
    (patch: Partial<T>) => {
      pendingPatch.current = { ...(pendingPatch.current ?? {}), ...patch };
      scheduleFlush();
    },
    [scheduleFlush],
  );

  return { schedule, cancel, flush };
}
