import { describe, it, expect } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { InfoTooltip } from '../InfoTooltip';

function Subject(props: { highlighted?: boolean }) {
  return (
    <div>
      <InfoTooltip id="tip-1" trigger="En route" highlighted={props.highlighted}>
        <p>HelloAsso a émis le virement.</p>
        <button type="button">J&apos;ai reçu ce virement</button>
      </InfoTooltip>
      <button type="button">ailleurs</button>
    </div>
  );
}

const tip = () => screen.getByRole('tooltip', { hidden: true });
const trigger = () => screen.getByRole('button', { name: 'En route' });

describe('InfoTooltip', () => {
  it('is closed by default and described by its content', () => {
    render(<Subject />);
    expect(tip()).not.toBeVisible();
    expect(trigger()).toHaveAttribute('aria-describedby', 'tip-1');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
  });

  it('opens on hover and closes when the pointer leaves', () => {
    render(<Subject />);
    fireEvent.mouseEnter(trigger().parentElement!);
    expect(tip()).toBeVisible();
    fireEvent.mouseLeave(trigger().parentElement!);
    expect(tip()).not.toBeVisible();
  });

  it('opens on keyboard focus and stays open while the focus is on its content', () => {
    render(<Subject />);
    act(() => trigger().focus());
    expect(tip()).toBeVisible();

    const inner = screen.getByRole('button', { name: "J'ai reçu ce virement" });
    act(() => inner.focus());
    expect(tip()).toBeVisible();

    act(() => screen.getByRole('button', { name: 'ailleurs' }).focus());
    expect(tip()).not.toBeVisible();
  });

  it('a click or tap pins it; a second one unpins it', () => {
    render(<Subject />);
    fireEvent.click(trigger());
    expect(tip()).toBeVisible();
    expect(trigger()).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(trigger());
    expect(tip()).not.toBeVisible();
  });

  it('a click on a control inside does not close it, a click outside does', () => {
    render(<Subject />);
    fireEvent.click(trigger());
    const inner = screen.getByRole('button', { name: "J'ai reçu ce virement" });
    fireEvent.mouseDown(inner);
    fireEvent.click(inner);
    expect(tip()).toBeVisible();

    fireEvent.mouseDown(screen.getByRole('button', { name: 'ailleurs' }));
    expect(tip()).not.toBeVisible();
  });

  it('Escape closes it and gives the focus back to the trigger', () => {
    render(<Subject />);
    fireEvent.click(trigger());
    act(() => screen.getByRole('button', { name: "J'ai reçu ce virement" }).focus());

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(tip()).not.toBeVisible();
    expect(trigger()).toHaveFocus();
  });

  it('a modal opened from it neither unpins it (click) nor closes it (Escape)', () => {
    render(
      <div>
        <InfoTooltip id="tip-m" trigger="En route"><button type="button">ouvrir</button></InfoTooltip>
        <div role="dialog" aria-modal="true"><button type="button">Pas encore</button></div>
      </div>,
    );
    fireEvent.click(trigger());
    const modalButton = screen.getByRole('button', { name: 'Pas encore' });
    fireEvent.mouseDown(modalButton);
    fireEvent.keyDown(modalButton, { key: 'Escape' });
    expect(screen.getByRole('tooltip', { hidden: true })).toBeVisible();
  });

  it('opens when highlighted from outside (bar segment hover)', () => {
    const { rerender } = render(<Subject highlighted />);
    expect(tip()).toBeVisible();
    rerender(<Subject highlighted={false} />);
    expect(tip()).not.toBeVisible();
  });

  it('a controlled group keeps a single tooltip pinned', () => {
    function Group() {
      const [pinned, setPinned] = useState<string | null>(null);
      return (
        <div>
          {['a', 'b'].map((k) => (
            <InfoTooltip key={k} id={`t-${k}`} trigger={k} pinned={pinned === k} onPinnedChange={(p) => setPinned(p ? k : null)}>
              <span>{`content ${k}`}</span>
            </InfoTooltip>
          ))}
        </div>
      );
    }
    render(<Group />);
    fireEvent.click(screen.getByRole('button', { name: 'a' }));
    fireEvent.click(screen.getByRole('button', { name: 'b' }));
    expect(screen.getByText('content a')).not.toBeVisible();
    expect(screen.getByText('content b')).toBeVisible();
  });
});
