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

  const styles: Record<VopResult, string> = {
    MATCH: 'bg-green/8 border-l-[3px] border-green text-green',
    CLOSE_MATCH: 'bg-yellow/8 border-l-[3px] border-yellow text-yellow',
    NO_MATCH: 'bg-red/8 border-l-[3px] border-red text-red',
    NOT_POSSIBLE: 'bg-muted/8 border-l-[3px] border-muted text-text-2',
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
    <div className={`rounded-[8px] p-[10px] text-[12px] mt-[6px] ${styles[vopResult]}`}>
      {getMessage()}
    </div>
  );
}
