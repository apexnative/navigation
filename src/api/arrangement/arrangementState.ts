import ANNTurboModule from '../../adapters/NativeANNTurboModule';
import { onNativeEvent } from '../../engine/native';
import { placeOf } from '../../engine/places';
import type { ArrangementChangedEvent } from '../../interfaces/Events';

/** How a screen of a pair (`defineArrangement`) shows now. */
export interface ArrangementState {
  /** Which view of the pair this screen is. */
  placement: 'primary' | 'secondary';
  /**
   * How the pair lies: `sideBySide`, `stacked` (one above the other), `overlay` (one over the other, both full size),
   * `single` (only one view is in the window).
   */
  layout: 'sideBySide' | 'stacked' | 'overlay' | 'single';
  /** `overlay`: this view lies over the other one. */
  front: boolean;
  /** `single`: this screen is out of the window. */
  hidden: boolean;
}

// The state of a primary shown alone, where the native side shows no pair.
const ALONE: ArrangementState = Object.freeze({
  placement: 'primary',
  layout: 'single',
  front: false,
  hidden: false,
});

// The states read so far, by screen id, until the screen unmounts.
const stateById = new Map<string, ArrangementState>();
const listeners = new Map<string, Set<() => void>>();
let listening = false;

/** The state of a screen of a pair; null for a screen outside one. */
export function getArrangementState(screenId: string): ArrangementState | null {
  const place = placeOf(screenId);
  if (!place?.arrangement) return null;
  if (place.arrangement.alone) return ALONE;
  listen();
  const known = stateById.get(screenId);
  if (known) return known;
  const read = readNative(screenId);
  if (read) stateById.set(screenId, read);
  return read ?? null;
}

/** Calls `onChange` when the screen's state in its pair changes; returns the function that stops it. */
export function subscribeArrangementState(screenId: string, onChange: () => void): () => void {
  listen();
  const own = listeners.get(screenId) ?? new Set<() => void>();
  own.add(onChange);
  listeners.set(screenId, own);
  return () => {
    own.delete(onChange);
    if (own.size === 0) listeners.delete(screenId);
  };
}

/** The screen unmounted: its state goes. */
export function forgetArrangementState(screenId: string) {
  stateById.delete(screenId);
  listeners.delete(screenId);
}

function listen() {
  if (listening) return;
  listening = true;
  onNativeEvent<ArrangementChangedEvent>('ArrangementChanged', ({ componentId, ...state }) => {
    // Only the screens that read their state keep it.
    if (!stateById.has(componentId) && !listeners.has(componentId)) return;
    const next = toState(state);
    if (!next) return;
    const current = stateById.get(componentId);
    if (current && sameState(current, next)) return;
    stateById.set(componentId, next);
    listeners.get(componentId)?.forEach((onChange) => onChange());
  });
}

function readNative(screenId: string): ArrangementState | undefined {
  // No native module in tests with the mock.
  const read = ANNTurboModule?.getArrangementState?.(screenId) as Partial<ArrangementState> | undefined;
  return read ? toState(read) : undefined;
}

function toState(value: Partial<ArrangementState>): ArrangementState | undefined {
  if (value.placement !== 'primary' && value.placement !== 'secondary') return undefined;
  return {
    placement: value.placement,
    layout: value.layout ?? 'single',
    front: value.front ?? false,
    hidden: value.hidden ?? false,
  };
}

function sameState(a: ArrangementState, b: ArrangementState): boolean {
  return a.placement === b.placement && a.layout === b.layout && a.front === b.front && a.hidden === b.hidden;
}
