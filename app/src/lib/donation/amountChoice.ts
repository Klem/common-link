import { AmountSource, type AmountChoiceStats } from '@/lib/gtm';

/**
 * Records the amounts a donor picks before submitting, for the `begin_checkout` push.
 *
 * Counting rules:
 * - one record per suggested-amount click, one per typed amount when the free field loses focus
 *   (the caller's job — typing "25" must not count "2" then "25");
 * - a record equal to the previous amount is ignored whatever its source: the free field displays
 *   the clicked preset, so focusing and leaving it unchanged is not a new choice;
 * - a non-finite amount (empty field) is ignored.
 */
export interface AmountChoiceTracker {
  record(amount: number | undefined, source: AmountSource): void;
  /**
   * Stats for [submittedAmount]. When it differs from the last recorded amount — typed, then
   * submitted with Enter without leaving the field — it first counts as one last typed choice.
   */
  finalize(submittedAmount: number): AmountChoiceStats;
}

/** Creates an empty {@link AmountChoiceTracker} — one per form instance. */
export function createAmountChoiceTracker(): AmountChoiceTracker {
  let firstAmount: number | null = null;
  let lastAmount: number | null = null;
  let lastSource: AmountSource = AmountSource.CUSTOM;
  let changes = 0;

  const record = (amount: number | undefined, source: AmountSource) => {
    if (amount === undefined || !Number.isFinite(amount) || amount === lastAmount) return;
    if (firstAmount === null) firstAmount = amount;
    lastAmount = amount;
    lastSource = source;
    changes += 1;
  };

  return {
    record,
    finalize(submittedAmount) {
      record(submittedAmount, AmountSource.CUSTOM);
      return { source: lastSource, firstAmount: firstAmount ?? submittedAmount, changes: Math.max(changes, 1) };
    },
  };
}
