'use client';

import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';

interface Props {
  isOpen: boolean;
  title: string;
  /** Accessible name of the close button. */
  closeLabel: string;
  onClose: () => void;
  children: ReactNode;
  /**
   * Actions pinned to the bottom edge, outside the scrolling body.
   *
   * A payout's one action is the reason its panel is opened at all — leaving it at the end of the
   * detail list put it below the fold on a narrow window, on exactly the rows that have one.
   */
  footer?: ReactNode;
}

/**
 * Right-hand panel used by the Payments tab for a payout's detail.
 *
 * A panel rather than a modal: the journal stays on screen behind it, so the association keeps the
 * row it came from in view.
 *
 * Its height is fixed rather than filled: a panel stretched to the viewport left a column of blank
 * under six lines of detail, and made the close button and the actions drift apart depending on how
 * much the payout happened to carry. Header and footer hold their edges, the body scrolls between
 * them.
 *
 * Closes on Escape and on a click on the veil. Focus moves into the panel on open so a keyboard
 * user is not left behind on the row that opened it.
 */
export function SidePanel({ isOpen, title, closeLabel, onClose, children, footer }: Props) {
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    panelRef.current?.focus();
    const onKeyDown = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="ov on side-panel-ov" onClick={onClose}>
      <div
        ref={panelRef}
        className="side-panel"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="side-panel-h">
          <h3>{title}</h3>
          <button type="button" className="mod-x" aria-label={closeLabel} onClick={onClose}>
            ×
          </button>
        </div>
        <div className="side-panel-b">{children}</div>
        {footer && <div className="side-panel-f">{footer}</div>}
      </div>
    </div>
  );
}
