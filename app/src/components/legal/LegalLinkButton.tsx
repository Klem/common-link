'use client';

import type { ReactNode } from 'react';

interface Props {
  onClick: () => void;
  className: string;
  children: ReactNode;
  /** Matches the host form's inert state — spans have no native `disabled`, so this is manual. */
  disabled?: boolean;
}

/**
 * Text-styled trigger that opens a {@link LegalDocumentModal}.
 *
 * Deliberately a `<span role="button">`, not a real `<button>`: every call site sits inside a
 * `<label htmlFor="…">` for a checkbox. `<button>` is a "labelable" element per the HTML spec, so
 * nesting one inside that `<label>` gives the label two labelable descendants — the checkbox
 * becomes ambiguous to resolve by label text (both for testing-library's `getByLabelText` and,
 * per the same spec ambiguity, some assistive tech). `<span>` isn't labelable, so it can't compete.
 *
 * Because a bare `<span>` (even with `role="button"`) is neither labelable nor "interactive
 * content", a real browser's label click-forwarding algorithm treats a click on it exactly like a
 * click on the label's own text — it toggles the labeled checkbox too, unless prevented. Calling
 * `preventDefault()` on the click suppresses that forwarding (jsdom does not model this
 * forwarding at all, so this cannot be exercised by a unit test — verified against the spec, not
 * a browser).
 */
export function LegalLinkButton({ onClick, className, children, disabled = false }: Props) {
  return (
    <span
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled}
      className={className}
      style={disabled ? { opacity: 0.5, pointerEvents: 'none' } : undefined}
      onClick={disabled ? undefined : (e) => { e.preventDefault(); onClick(); }}
      onKeyDown={disabled ? undefined : (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick();
        }
      }}
    >
      {children}
    </span>
  );
}
