import { useTranslations } from 'next-intl';
import type { BeneficiaryDto } from '@/types/beneficiary';
import { BeneficiaryRow } from './BeneficiaryRow';

interface BeneficiaryListProps {
  /** Beneficiaries to display. */
  beneficiaries: BeneficiaryDto[];
  /** True while the list is being loaded. */
  isLoading: boolean;
  /** Called when the user deletes a beneficiary. */
  onDeleteBeneficiary: (id: string) => void;
  /** Called when the user adds an IBAN to a beneficiary. */
  onAddIban: (beneficiaryId: string, iban: string) => void;
  /** Called when the user deletes a beneficiary IBAN. */
  onDeleteIban: (beneficiaryId: string, ibanId: string) => void;
  /** Called when the user triggers VOP verification on an IBAN. */
  onVerifyVop: (beneficiaryId: string, ibanId: string) => void;
  /** UUID of the IBAN currently being VOP-verified, or null. */
  verifyingIbanId: string | null;
}

/**
 * Card listing all beneficiaries for an association.
 *
 * Shows a loading skeleton, an empty state, or the list of BeneficiaryRow entries
 * separated by dividers.
 */
export function BeneficiaryList({
  beneficiaries,
  isLoading,
  onDeleteBeneficiary,
  onAddIban,
  onDeleteIban,
  onVerifyVop,
  verifyingIbanId,
}: BeneficiaryListProps) {
  const t = useTranslations('dashboard');

  return (
    <div className="bg-bg-2 border border-border rounded-xl overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-[20px] border-b border-border">
        <h2 className="font-display font-bold text-[16px] text-text">
          {t('beneficiaries.list.title')}
        </h2>
        <span className="bg-green/12 text-green rounded-full px-[10px] py-[3px] text-[12px] font-bold">
          {t('beneficiaries.list.count', { count: beneficiaries.length })}
        </span>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-[40px]">
          <span className="inline-block w-6 h-6 border-2 border-border border-t-green rounded-full animate-spin" />
        </div>
      ) : beneficiaries.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-[40px] text-center px-[24px]">
          <p className="text-muted text-[14px]">{t('beneficiaries.list.empty')}</p>
          <p className="text-muted text-[12px] mt-[4px]">{t('beneficiaries.list.emptyHint')}</p>
        </div>
      ) : (
        <div>
          {beneficiaries.map((beneficiary, index) => (
            <div key={beneficiary.id}>
              {index > 0 && <div className="border-t border-border" />}
              <BeneficiaryRow
                beneficiary={beneficiary}
                onDeleteBeneficiary={onDeleteBeneficiary}
                onAddIban={onAddIban}
                onDeleteIban={onDeleteIban}
                onVerifyVop={onVerifyVop}
                verifyingIbanId={verifyingIbanId}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
