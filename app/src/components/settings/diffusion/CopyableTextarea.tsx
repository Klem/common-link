'use client';

import { useState } from 'react';

interface Props {
  value: string;
  rows: number;
  label: string;
  hint?: string;
  copyLabel: string;
  copiedLabel: string;
}

/** Read-only code field with its own copy button and transient "copied" state. */
export function CopyableTextarea({ value, rows, label, hint, copyLabel, copiedLabel }: Props) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <>
      <label className="fl" style={{ marginTop: 8 }}>{label}</label>
      {hint && <p className="fhint" style={{ marginBottom: 4 }}>{hint}</p>}
      <textarea
        className="fi"
        readOnly
        rows={rows}
        value={value}
        style={{ fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }}
        onFocus={(e) => e.target.select()}
      />
      <div className="frow-actions" style={{ marginTop: 4 }}>
        <button type="button" className="btn btn-secondary btn-sm" onClick={copy}>
          {copied ? copiedLabel : copyLabel}
        </button>
      </div>
    </>
  );
}
