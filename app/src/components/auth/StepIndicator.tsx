'use client';

import { Fragment } from 'react';

interface StepIndicatorProps {
  steps: string[];
  currentStep: number; // 1-indexed
}

export function StepIndicator({ steps, currentStep }: StepIndicatorProps) {
  return (
    <div className="step-indicator">
      {steps.map((label, i) => {
        const stepNum = i + 1;
        const isDone = stepNum < currentStep;
        const isActive = stepNum === currentStep;
        const state = isDone ? 'done' : isActive ? 'active' : 'pending';

        return (
          <Fragment key={stepNum}>
            <div className={`step-item ${state}`}>
              <div className="step-dot">{isDone ? '✓' : stepNum}</div>
              <span className="sr-only sm:not-sr-only">{label}</span>
            </div>
            {i < steps.length - 1 && (
              <div className={`step-connector ${isDone ? 'done' : 'pending'}`} />
            )}
          </Fragment>
        );
      })}
    </div>
  );
}
