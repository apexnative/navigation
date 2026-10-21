import { onNativeEvent } from '../../engine/native';
import type {
  ComponentDidAppearEvent,
  ComponentDidDisappearEvent,
} from '../../interfaces/ComponentEvents';
import type { NavigationStateChangedEvent } from '../../interfaces/Events';

/**
 * What the user sees: the screen and the selected tab. The native side sends it when it changes, and before a command
 * that changed it resolves.
 */
export type NavigationState = NavigationStateChangedEvent;

let state: NavigationState = { screen: null, tab: null };
const listeners = new Set<(state: NavigationState) => void>();
// The screens that appeared and haven't disappeared since, for a component that mounts in one later.
const appeared = new Set<string>();
let listening = false;

/** Keeps the last state from the native side and the screens shown; the app starts it before its first root. */
export function listenToNavigationState() {
  if (listening) return;
  listening = true;
  onNativeEvent<NavigationStateChangedEvent>('NavigationStateChanged', (next) => {
    state = next;
    listeners.forEach((listener) => listener(state));
  });
  onNativeEvent<ComponentDidAppearEvent>('ComponentDidAppear', ({ componentId }) => {
    appeared.add(componentId);
  });
  onNativeEvent<ComponentDidDisappearEvent>('ComponentDidDisappear', ({ componentId }) => {
    appeared.delete(componentId);
  });
}

/** Whether the screen has appeared and not disappeared since, as the native side told it. */
export function hasAppeared(screenId: string): boolean {
  return appeared.has(screenId);
}

/** The screen is gone: a disappearance that never came doesn't count it as shown for a screen with its id later. */
export function forgetAppeared(screenId: string): void {
  appeared.delete(screenId);
}

export function getNavigationState(): NavigationState {
  return state;
}

/** Calls the listener on each change; returns the function that stops it. */
export function onNavigationStateChange(listener: (state: NavigationState) => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * Resolves once the native side sent a state newer than `since` with a screen in it, at the latest after `timeout`:
 * the state then shows what the commands before it changed, such as the screen of a new root.
 */
export function whenScreenAfter(since: NavigationState, timeout: number): Promise<void> {
  const shows = (next: NavigationState) => next !== since && next.screen !== null;
  if (shows(state)) return Promise.resolve();
  return new Promise((resolve) => {
    const done = () => {
      clearTimeout(timer);
      stop();
      resolve();
    };
    const timer = setTimeout(done, timeout);
    const stop = onNavigationStateChange((next) => {
      if (shows(next)) done();
    });
  });
}

/**
 * Resolves once the state shows the tab: a tab by index at once when it shows already, otherwise on the next change of
 * the state, and at the latest after `timeout` (a tab by id that shows already changes nothing).
 */
export function whenTabShows(tab: number | string, timeout = 300): Promise<void> {
  if (typeof tab === 'number' && state.tab === tab) return Promise.resolve();
  return new Promise((resolve) => {
    const done = () => {
      clearTimeout(timer);
      stop();
      resolve();
    };
    const timer = setTimeout(done, timeout);
    const stop = onNavigationStateChange((next) => {
      if (typeof tab !== 'number' || next.tab === tab) done();
    });
  });
}
