import { useSyncExternalStore } from 'react';

let count = 0;
const listeners = new Set<() => void>();

/** A store outside React, as Redux or Zustand keep one: the native screen of section 18 reads it in `useProps`. */
export const counter = {
  increment() {
    count += 1;
    listeners.forEach((listener) => listener());
  },
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  get: () => count,
};

/** The count, rendering again when it changes. */
export function useCount(): number {
  return useSyncExternalStore(counter.subscribe, counter.get);
}
