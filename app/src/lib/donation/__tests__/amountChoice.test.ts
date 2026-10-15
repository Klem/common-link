import { describe, it, expect } from 'vitest';
import { createAmountChoiceTracker } from '../amountChoice';

describe('createAmountChoiceTracker', () => {
  it('counts one choice for a single preset click', () => {
    const tracker = createAmountChoiceTracker();
    tracker.record(25, 'preset');
    expect(tracker.finalize(25)).toEqual({ source: 'preset', firstAmount: 25, changes: 1 });
  });

  it('counts every distinct choice and keeps the first one', () => {
    const tracker = createAmountChoiceTracker();
    tracker.record(10, 'preset');
    tracker.record(50, 'preset');
    tracker.record(30, 'custom');
    expect(tracker.finalize(30)).toEqual({ source: 'custom', firstAmount: 10, changes: 3 });
  });

  it('ignores re-choosing the same amount, whatever its source', () => {
    const tracker = createAmountChoiceTracker();
    tracker.record(25, 'preset');
    tracker.record(25, 'preset');
    // The free field displays the clicked preset: focusing and leaving it is not a new choice.
    tracker.record(25, 'custom');
    expect(tracker.finalize(25)).toEqual({ source: 'preset', firstAmount: 25, changes: 1 });
  });

  it('ignores an empty free field', () => {
    const tracker = createAmountChoiceTracker();
    tracker.record(Number.NaN, 'custom');
    tracker.record(undefined, 'custom');
    tracker.record(50, 'preset');
    expect(tracker.finalize(50)).toEqual({ source: 'preset', firstAmount: 50, changes: 1 });
  });

  it('counts an amount typed then submitted with Enter, without leaving the field', () => {
    const tracker = createAmountChoiceTracker();
    tracker.record(25, 'preset');
    expect(tracker.finalize(30)).toEqual({ source: 'custom', firstAmount: 25, changes: 2 });
  });

  it('reports one typed choice when nothing was recorded', () => {
    const tracker = createAmountChoiceTracker();
    expect(tracker.finalize(40)).toEqual({ source: 'custom', firstAmount: 40, changes: 1 });
  });
});
