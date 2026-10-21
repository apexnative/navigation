import { useEffect, useLayoutEffect, useRef, useSyncExternalStore } from 'react';
import { getHinge, type Hinge, subscribeHinge } from '../hinge/hinge';

/**
 * The device's hinge: its state and angle. Null on a device without a hinge, and for the moment before the first value
 * comes. The screen renders again on every change, often while the hinge moves: for an animation driven by the angle,
 * `useHingeChange`. For the layout of a screen the fold calls for, `defineArrangement` and `useReservedRegions`.
 */
export function useHinge(): Hinge | null {
  return useSyncExternalStore(subscribeHinge, getHinge);
}

/**
 * Calls `listener` on every change of the hinge, without rendering again: for animations driven by the angle.
 *
 * @example
 * const lid = useRef(new Animated.Value(180)).current;
 * useHingeChange((hinge) => lid.setValue(hinge?.angle ?? 180));
 */
export function useHingeChange(listener: (hinge: Hinge | null) => void): void {
  const latest = useRef(listener);
  useLayoutEffect(() => {
    latest.current = listener;
  });
  useEffect(() => subscribeHinge((hinge) => latest.current(hinge)), []);
}
