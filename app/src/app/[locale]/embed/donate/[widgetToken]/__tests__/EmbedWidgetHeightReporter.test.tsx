import { describe, it, expect, vi, afterEach } from 'vitest';
import { render } from '@testing-library/react';
import { EmbedWidgetHeightReporter } from '../EmbedWidgetHeightReporter';

class FakeResizeObserver {
  observe = vi.fn();
  disconnect = vi.fn();
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('EmbedWidgetHeightReporter', () => {
  it('does not post anything when the page is not framed', () => {
    vi.stubGlobal('ResizeObserver', FakeResizeObserver);
    const postMessage = vi.fn();
    // jsdom default: window.parent === window (not framed).

    render(<EmbedWidgetHeightReporter parentOrigin="https://asso.example.org" />);

    expect(postMessage).not.toHaveBeenCalled();
  });

  it('posts the body height to the exact parentOrigin when framed', () => {
    vi.stubGlobal('ResizeObserver', FakeResizeObserver);
    const postMessage = vi.fn();
    const fakeParent = { postMessage };
    vi.spyOn(window, 'parent', 'get').mockReturnValue(fakeParent as unknown as Window);
    Object.defineProperty(document.body, 'scrollHeight', { value: 480, configurable: true });

    render(<EmbedWidgetHeightReporter parentOrigin="https://asso.example.org" />);

    expect(postMessage).toHaveBeenCalledWith(
      { type: 'cl-widget-height', height: 480 },
      'https://asso.example.org',
    );
  });
});
