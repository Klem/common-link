'use client';

import { useEffect } from 'react';

/** Message contract with `public/landing.js` — both sides must agree on this literal. */
const MESSAGE_TYPE = 'cl-landing-height';

interface Props {
  /** Validated host origin (scheme://host[:port]) the height is posted to. Never "*". */
  parentOrigin: string;
}

/**
 * Reports the landing page height to the embedding host page so the loader can resize
 * its iframe to fit the content (no inner scrollbar, no fixed height guess).
 *
 * Renders nothing. No-op when the page is not framed, so a directly visited landing page
 * never posts anything.
 */
export function EmbedHeightReporter({ parentOrigin }: Props) {
  useEffect(() => {
    if (window.parent === window) return;

    let lastReported = 0;

    // `document.body`, never `documentElement`: the root element stretches to the iframe
    // viewport, so measuring it would report back the height the loader just applied — the
    // frame could then never shrink. The body box follows the content only.
    const post = () => {
      const height = Math.ceil(document.body.scrollHeight);
      // Sub-pixel rounding must not start a resize ping-pong with the loader.
      if (height <= 0 || Math.abs(height - lastReported) <= 2) return;
      lastReported = height;
      window.parent.postMessage({ type: MESSAGE_TYPE, height }, parentOrigin);
    };

    post();

    // Fonts and images land after hydration and change the height — observing the body
    // covers every reflow (section expansion, form errors, viewport change).
    const observer = new ResizeObserver(post);
    observer.observe(document.body);
    window.addEventListener('load', post);

    return () => {
      observer.disconnect();
      window.removeEventListener('load', post);
    };
  }, [parentOrigin]);

  return null;
}
