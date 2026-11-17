'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';

/** Whether an event comes from inside an open modal dialog (`aria-modal="true"`), which the tooltip leaves alone. */
function inModal(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest('[aria-modal="true"]') !== null;
}

export interface InfoTooltipProps {
  /** Id of the tooltip content, referenced by the trigger's `aria-describedby`. */
  id: string;
  /** Content of the trigger button (e.g. amount + label). */
  trigger: ReactNode;
  /** Tooltip content; may hold links and buttons (reachable with Tab from the trigger). */
  children: ReactNode;
  /** Class of the wrapper; `open` is added while the tooltip shows. */
  className?: string;
  /** Class of the trigger button. */
  triggerClassName?: string;
  /** Class of the tooltip container (`role="tooltip"`). */
  tipClassName?: string;
  /**
   * Pinned state, when the parent keeps a single tooltip pinned at a time. Omit both `pinned` and `onPinnedChange` to
   * let the component pin itself.
   */
  pinned?: boolean;
  onPinnedChange?: (pinned: boolean) => void;
  /** Opens the tooltip from outside, e.g. while the matching bar segment is hovered. */
  highlighted?: boolean;
}

/**
 * Accessible tooltip whose content can be interactive (link, button), e.g. the steps of the "Où en sont vos dons" bar.
 *
 * Opens on hover, on keyboard focus (and stays open while the focus is inside it), and on click / tap, which pins it
 * (useful on mobile). Closes on Escape (focus back on the trigger) and on a click outside; a click on a link or button
 * inside the tooltip neither closes nor unpins it. The trigger is a real `<button>` carrying `aria-describedby` and,
 * when pinned, `aria-expanded`; the content (`role="tooltip"`) is its sibling, never its child, so nested controls stay
 * valid HTML. While closed the content is `hidden`, so its controls are not in the tab order.
 */
export function InfoTooltip({
  id, trigger, children, className = '', triggerClassName = '', tipClassName = '',
  pinned: pinnedProp, onPinnedChange, highlighted = false,
}: InfoTooltipProps) {
  const [ownPinned, setOwnPinned] = useState(false);
  const controlled = pinnedProp !== undefined;
  const pinned = controlled ? pinnedProp : ownPinned;
  const setPinned = useCallback((value: boolean) => {
    if (!controlled) setOwnPinned(value);
    onPinnedChange?.(value);
  }, [controlled, onPinnedChange]);

  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  /** Escape closes a hovered / focused tooltip until the pointer or the focus leaves it. */
  const [dismissed, setDismissed] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  const open = pinned || (!dismissed && (hovered || focused || highlighted));

  useEffect(() => {
    if (!pinned) return;
    const onDocumentDown = (e: MouseEvent | TouchEvent) => {
      // A modal opened from the tooltip (e.g. the receipt dialog) is not "outside": the tooltip stays pinned so the
      // focus can come back to it when the modal closes.
      if (inModal(e.target)) return;
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) setPinned(false);
    };
    document.addEventListener('mousedown', onDocumentDown);
    document.addEventListener('touchstart', onDocumentDown);
    return () => {
      document.removeEventListener('mousedown', onDocumentDown);
      document.removeEventListener('touchstart', onDocumentDown);
    };
  }, [pinned, setPinned]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape' || inModal(e.target)) return;
      setPinned(false);
      setDismissed(true);
      if (wrapperRef.current?.contains(document.activeElement)) buttonRef.current?.focus();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, setPinned]);

  return (
    <div
      ref={wrapperRef}
      className={`${className}${open ? ' open' : ''}`}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => { setHovered(false); setDismissed(false); }}
      onFocus={() => setFocused(true)}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) {
          setFocused(false);
          setDismissed(false);
        }
      }}
    >
      <button
        ref={buttonRef}
        type="button"
        className={triggerClassName}
        aria-describedby={id}
        aria-expanded={pinned}
        onClick={() => { setDismissed(false); setPinned(!pinned); }}
      >
        {trigger}
      </button>
      <div id={id} role="tooltip" className={tipClassName} hidden={!open}>
        {children}
      </div>
    </div>
  );
}
