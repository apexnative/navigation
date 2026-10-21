import ANNTurboModule from '../../adapters/NativeANNTurboModule';
import { onNativeEvent } from '../../engine/native';
import type { HingeChangedEvent } from '../../interfaces/Events';

/** The device's hinge. */
export interface Hinge {
  /**
   * `closed`, `partiallyOpen`, `fullyOpen` (open as far as the device allows), or `unknown`. On Android `closed` is
   * told by the hinge angle sensor: the app's window has no fold and the angle is below 90°.
   */
  state: 'closed' | 'partiallyOpen' | 'fullyOpen' | 'unknown';
  /**
   * Degrees: 0 closed, 180 open flat, beyond 180 on Android devices that fold back. Null where the platform gives no
   * angle: on Android without a hinge sensor, and on iOS while the state is unknown.
   */
  angle: number | null;
}

let current: Hinge | null = null;
let known = false;
const listeners = new Set<(hinge: Hinge | null) => void>();
let stopEvents: (() => void) | undefined;

/** The last hinge value; null without a hinge and before the native side sent the first one. */
export function getHinge(): Hinge | null {
  if (!known) {
    const read = readNative();
    if (read !== undefined) {
      current = read;
      known = true;
    }
  }
  return current;
}

/**
 * Calls `listener` on every change of the hinge; returns the function that stops it. The native side watches the hinge
 * only while something listens: a hinge sensor on Android costs power.
 */
export function subscribeHinge(listener: (hinge: Hinge | null) => void): () => void {
  listeners.add(listener);
  if (listeners.size === 1) start();
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) stop();
  };
}

function start() {
  stopEvents = onNativeEvent<HingeChangedEvent>('HingeChanged', (event) => {
    const next = toHinge(event);
    if (known && sameHinge(current, next)) return;
    current = next;
    known = true;
    listeners.forEach((listener) => listener(next));
  });
  ANNTurboModule?.setHingeUpdates?.(true);
}

function stop() {
  ANNTurboModule?.setHingeUpdates?.(false);
  stopEvents?.();
  stopEvents = undefined;
  // Not watched any more: the next listener starts from what the native side says then.
  known = false;
  current = null;
}

// Undefined: the native side knows nothing yet (no update since it started watching).
function readNative(): Hinge | null | undefined {
  // No native module in tests with the mock.
  const read = ANNTurboModule?.getHinge?.() as Partial<HingeChangedEvent> | undefined;
  if (!read || typeof read.state !== 'string') return undefined;
  return toHinge(read);
}

function toHinge(event: Partial<HingeChangedEvent>): Hinge | null {
  if (typeof event.state !== 'string') return null;
  return { state: event.state, angle: typeof event.angle === 'number' ? event.angle : null };
}

function sameHinge(a: Hinge | null, b: Hinge | null): boolean {
  return a === b || (a !== null && b !== null && a.state === b.state && a.angle === b.angle);
}
