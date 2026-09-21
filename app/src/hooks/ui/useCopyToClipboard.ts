'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

/** How long the "copied" confirmation stays on screen, in milliseconds. */
const CONFIRMATION_MS = 2000;

/**
 * Writes a value to the clipboard and exposes a transient confirmation flag.
 *
 * The flag resets after {@link CONFIRMATION_MS}; the pending timer is cleared on unmount so a
 * component removed during the confirmation window never sets state after it is gone.
 *
 * @returns `copied` — true during the confirmation window — and `copy(value)`.
 */
export function useCopyToClipboard() {
  const [copied, setCopied] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);

  const copy = useCallback(async (value: string) => {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), CONFIRMATION_MS);
  }, []);

  return { copied, copy };
}
