import ANNTurboModule from '../../adapters/NativeANNTurboModule';
import { onNativeEvent } from '../../engine/native';
import type { ScreenInsetsChangedEvent } from '../../interfaces/Events';

/**
 * How much of a screen's content the bars and the system cover on each side, past the screen's own padding: points on
 * iOS, dp on Android.
 */
export interface EdgeInsets {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

const NONE: EdgeInsets = Object.freeze({ top: 0, right: 0, bottom: 0, left: 0 });

// The insets of the screens read so far, by screen id, until the screen unmounts.
const insetsById = new Map<string, EdgeInsets>();
const listeners = new Map<string, Set<() => void>>();
let listening = false;

/**
 * The insets of a screen: the last ones the native side sent, or read from it at once, so a screen's first render
 * has them when the screen is laid out; none before that.
 */
export function getScreenInsets(screenId: string): EdgeInsets {
  listen();
  const known = insetsById.get(screenId);
  if (known) return known;
  const read = readNative(screenId);
  if (!read) return NONE;
  insetsById.set(screenId, read);
  return read;
}

/** Calls `onChange` when the screen's insets change; returns the function that stops it. */
export function subscribeScreenInsets(screenId: string, onChange: () => void): () => void {
  listen();
  const own = listeners.get(screenId) ?? new Set<() => void>();
  own.add(onChange);
  listeners.set(screenId, own);
  return () => {
    own.delete(onChange);
    if (own.size === 0) listeners.delete(screenId);
  };
}

/** The screen unmounted: its insets go. */
export function forgetScreenInsets(screenId: string) {
  insetsById.delete(screenId);
  listeners.delete(screenId);
}

function listen() {
  if (listening) return;
  listening = true;
  onNativeEvent<ScreenInsetsChangedEvent>('ScreenInsetsChanged', ({ componentId, ...insets }) => {
    // Only the screens that read their insets keep them.
    if (!insetsById.has(componentId) && !listeners.has(componentId)) return;
    const next = toInsets(insets);
    const current = insetsById.get(componentId);
    if (current && sameInsets(current, next)) return;
    insetsById.set(componentId, next);
    listeners.get(componentId)?.forEach((onChange) => onChange());
  });
}

function readNative(screenId: string): EdgeInsets | undefined {
  // No native module in tests with the mock.
  const read = ANNTurboModule?.getScreenInsets?.(screenId) as Partial<EdgeInsets> | undefined;
  return read && typeof read.top === 'number' ? toInsets(read) : undefined;
}

function toInsets(value: Partial<EdgeInsets>): EdgeInsets {
  return {
    top: value.top ?? 0,
    right: value.right ?? 0,
    bottom: value.bottom ?? 0,
    left: value.left ?? 0,
  };
}

function sameInsets(a: EdgeInsets, b: EdgeInsets) {
  return a.top === b.top && a.right === b.right && a.bottom === b.bottom && a.left === b.left;
}
