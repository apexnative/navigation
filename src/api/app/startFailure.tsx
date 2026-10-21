import React, { type ComponentType, useContext, useSyncExternalStore } from 'react';
import type { ErrorFallbackProps } from '../screens/ScreenBoundary';
import { ScreenContext } from '../screens/registerScreens';

/** The screen a failed start shows as the first root, in place of the splash screen: `errorFallback`. */
export const START_FAILURE_SCREEN = 'ApexNavigation.StartFailure';

interface StartFailure {
  error: unknown;
  /** Runs the start again, from `willMount`. */
  retry: () => void;
}

let failure: StartFailure | undefined;
const listeners = new Set<() => void>();

/** The start failed: the screen shows this error, or the new one of a try that failed again. */
export function setStartFailure(next: StartFailure) {
  failure = next;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The component of the screen: the app's `errorFallback` with the error of the start, its `retry` starts again. */
export function startFailureScreen(Fallback: ComponentType<ErrorFallbackProps>): ComponentType {
  function StartFailure() {
    const current = useSyncExternalStore(subscribe, () => failure);
    const screen = useContext(ScreenContext);
    if (!current || !screen) return null;
    return <Fallback error={current.error} screen={screen} retry={current.retry} />;
  }
  return StartFailure;
}
