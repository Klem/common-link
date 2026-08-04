'use client';

import { useState } from 'react';

interface CopyableCodeProps {
  /** Text shown in the code box and written to the clipboard. */
  value: string;
  /** Tooltip before copying. */
  copyLabel: string;
  /** Tooltip during the 2s confirmation window. */
  copiedLabel: string;
}

/**
 * Read-only code box with a copy-to-clipboard button and a transient "copied" state.
 *
 * Labels are injected so the component stays i18n-namespace agnostic.
 */
export function CopyableCode({ value, copyLabel, copiedLabel }: CopyableCodeProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="copyable-code">
      <code className="fi copyable-code-box">{value}</code>
      <button
        type="button"
        className="copyable-code-btn"
        title={copied ? copiedLabel : copyLabel}
        onClick={handleCopy}
      >
        {copied ? (
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
        ) : (
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
        )}
      </button>
    </div>
  );
}
