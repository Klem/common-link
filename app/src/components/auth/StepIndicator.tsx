'use client';

import { Fragment } from 'react';

interface StepIndicatorProps {
  steps: string[];
  currentStep: number; // 1-indexed
}

export function StepIndicator({ steps, currentStep }: StepIndicatorProps) {
  return (
    <div className="flex items-center mb-5">
      {steps.map((label, i) => {
        const stepNum = i + 1;
        const isDone = stepNum < currentStep;
        const isActive = stepNum === currentStep;

        return (
          <Fragment key={stepNum}>
            <div
              className={`flex items-center gap-[6px] text-[12px] transition-all duration-300 ${
                isDone ? 'text-green' : isActive ? 'text-text' : 'text-muted'
              }`}
            >
              <div
                className={`w-[22px] h-[22px] rounded-full border-[1.5px] flex items-center justify-center text-[10px] font-bold flex-shrink-0 transition-all duration-300 ${
                  isDone
                    ? 'bg-green border-green text-black'
                    : isActive
                      ? 'border-green text-green shadow-[0_0_8px_rgba(0,184,154,.28)]'
                      : 'border-muted text-muted'
                }`}
              >
                {isDone ? '✓' : stepNum}
              </div>
              {label}
            </div>
            {i < steps.length - 1 && (
              <div
                className={`flex-1 h-px mx-[7px] transition-all duration-300 ${
                  isDone ? 'bg-green' : 'bg-border'
                }`}
              />
            )}
          </Fragment>
        );
      })}
    </div>
  );
}
