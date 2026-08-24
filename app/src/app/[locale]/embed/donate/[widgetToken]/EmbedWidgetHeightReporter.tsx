'use client';

import { useEffect } from 'react';

/**
 * Message contract with `public/widget.js` — both sides must agree on this literal.
 *
 * Deliberately a different constant than `lp/[widgetToken]/EmbedHeightReporter`'s
 * `cl-landing-height`: `widget.js` and `landing.js` are independent loaders by design (see
 * `public/widget.js`'s file header), so their resize channels stay independent too.
 */
const MESSAGE_TYPE = 'cl-widget-height';

interface Props {
  /** Validated host origin (scheme://host[:port]) the height is posted to. Never "*". */
  parentOrigin: string;
}

/**
 * Reports the donation widget's height to the embedding host page so `widget.js` can resize its
 * iframe to fit the content (no inner scrollbar, no fixed 640px guess).
 *
 * Renders nothing. No-op when the page is not framed, so a directly visited embed page never
 * posts anything.
 */
export function EmbedWidgetHeightReporter({ parentOrigin }: Props) {
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

    // Fonts and form validation errors land after hydration and change the height — observing
    // the body covers every reflow.
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
