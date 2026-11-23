'use client';

import { useEffect, useRef } from 'react';
import SignaturePad from 'signature_pad';
import { toSignatureBlob } from '@/lib/signatureImage';

interface SignatureDrawPadProps {
  /** Called after each stroke with the exported PNG, or null when the pad is empty (or cleared). */
  onChange: (image: Blob | null) => void;
  /** Accessible name of the drawing area. */
  padLabel: string;
  /** Label of the clear button. */
  clearLabel: string;
}

/**
 * Hand-drawn signature on a `signature_pad` canvas. The backing store follows `devicePixelRatio`
 * (crisp strokes on HiDPI screens) and is re-sized with the window; the export is re-encoded by
 * {@link toSignatureBlob}, so its dimensions always respect the backend bounds. Transparent background.
 */
export function SignatureDrawPad({ onChange, padLabel, clearLabel }: SignatureDrawPadProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const padRef = useRef<SignaturePad | null>(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  // Incremented by every stroke and clear: an export finishing after a newer action is discarded.
  const exportSeq = useRef(0);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    // Ink color comes from the CSS `color` of `.sig-pad`, so the theme stays in globals.css.
    const penColor = getComputedStyle(canvas).color || 'black';
    const pad = new SignaturePad(canvas, { penColor, backgroundColor: 'rgba(0,0,0,0)' });
    padRef.current = pad;

    const resize = () => {
      const ratio = Math.max(window.devicePixelRatio || 1, 1);
      const strokes = pad.toData();
      canvas.width = canvas.offsetWidth * ratio;
      canvas.height = canvas.offsetHeight * ratio;
      canvas.getContext('2d')?.scale(ratio, ratio);
      // Resizing the backing store wipes it: redraw the strokes already made.
      pad.clear();
      pad.fromData(strokes);
    };

    const handleEndStroke = async () => {
      const seq = ++exportSeq.current;
      if (pad.isEmpty()) {
        onChangeRef.current(null);
        return;
      }
      let image: Blob | null;
      try {
        image = await toSignatureBlob(canvas, canvas.width, canvas.height);
      } catch {
        image = null;
      }
      // A stroke or a clear happened during the export: its own result wins.
      if (seq !== exportSeq.current || pad.isEmpty()) return;
      onChangeRef.current(image);
    };

    resize();
    window.addEventListener('resize', resize);
    pad.addEventListener('endStroke', handleEndStroke);
    return () => {
      window.removeEventListener('resize', resize);
      pad.removeEventListener('endStroke', handleEndStroke);
      pad.off();
      padRef.current = null;
    };
  }, []);

  const clear = () => {
    exportSeq.current += 1;
    padRef.current?.clear();
    onChangeRef.current(null);
  };

  return (
    <div>
      <canvas ref={canvasRef} className="sig-pad" aria-label={padLabel} role="img" />
      <div className="sig-pad-actions">
        <button type="button" className="btn btn-secondary btn-sm" onClick={clear}>
          {clearLabel}
        </button>
      </div>
    </div>
  );
}
