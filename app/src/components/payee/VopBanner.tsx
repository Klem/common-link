import { useTranslations } from 'next-intl';
import { VopResult } from '@/types/payee';

interface VopBannerProps {
  /** VOP result code determining the banner color and message. */
  vopResult: VopResult;
  /** Suggested name returned by VOP when result is CLOSE_MATCH. */
  suggestedName?: string | null;
}

/**
 * Inline banner rendered below an IBAN input to display the VOP verification result.
 *
 * Color and message vary by result:
 * - MATCH → green
 * - CLOSE_MATCH → yellow (includes suggested name)
 * - NO_MATCH → red
 * - NOT_POSSIBLE → muted
 */
export function VopBanner({ vopResult, suggestedName }: VopBannerProps) {
  const t = useTranslations('dashboard');

  // Reuses the dashboard `alert` family rather than ad-hoc Tailwind colours, so this banner reads
  // like every other inline notice in the association dashboard. NOT_POSSIBLE has no `alert-*`
  // equivalent — it is neither success nor failure — hence the one payee-specific modifier.
  const styles: Record<VopResult, string> = {
    MATCH: 'alert-success',
    CLOSE_MATCH: 'alert-warning',
    NO_MATCH: 'alert-error',
    NOT_POSSIBLE: 'payee-alert-neutral',
  };

  const getMessage = (): string => {
    switch (vopResult) {
      case VopResult.MATCH:
        return `✓ ${t('payees.iban.vop.match')}`;
      case VopResult.CLOSE_MATCH:
        return `≈ ${t('payees.iban.vop.closeMatch', { name: suggestedName ?? '' })}`;
      case VopResult.NO_MATCH:
        return `✗ ${t('payees.iban.vop.noMatch')}`;
      case VopResult.NOT_POSSIBLE:
        return `? ${t('payees.iban.vop.notPossible')}`;
    }
  };

  return (
    <div className={`alert payee-alert-inline ${styles[vopResult]}`}>
      {getMessage()}
    </div>
  );
}
