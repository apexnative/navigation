import type { ComponentType } from 'react';
import { getStartInfo } from '../../engine/native';
import type { LayoutRoot } from '../../interfaces/Layout';
import type { RootTransition } from '../layout/Root';

/** The screen of the app's splash in React Native: the first root of a cold start. */
export const SPLASH_SCREEN = 'ApexNavigation.Splash';

/**
 * The app's own splash in React Native: an animation, a progress. It shows after the native splash on a cold start
 * of the app, once per process: not after a reload of the JavaScript, and not when Android makes a new activity while
 * the JavaScript runs on.
 */
export interface SplashConfig {
  /**
   * What the splash shows: a component without props, over the whole window. It renders before `willMount` ends, so
   * it is a React root of its own, outside `appProvider` and `screenProvider`, which may need what `willMount`
   * prepares. The app's context is there (`useAppContext`), as `willMount` leaves it.
   */
  component: ComponentType;
  /** The least time the splash stays on screen, in ms, from when it shows: the length of its animation. `0` by default. */
  minDuration?: number;
  /**
   * When the splash leaves: `'auto'` (the default) once `willMount` ended and `minDuration` passed; `'manual'` waits
   * for `useSplash().hide()` too.
   */
  hide?: 'auto' | 'manual';
  /**
   * How the first root replaces the splash: what `Root(…).transition` takes, `'fade'` by default. A first root with a
   * transition of its own (`Root(…).transition(…)` other than `'platform'`) keeps it.
   */
  transition?: RootTransition;
}

/** The size of the native splash's logo: points on iOS, dp on Android. */
export interface SplashLogo {
  width: number;
  height: number;
}

/** What `useSplash` returns. */
export interface Splash {
  /**
   * Lets the splash leave, with `hide: 'manual'`: it still waits for `willMount` and `minDuration`. Does nothing once
   * it left, or without a splash.
   */
  hide(): void;
  /**
   * The logo of the native splash, centered in the window at this size, as `apex-navigation splash` made it: a splash
   * that starts from it continues the native one without a jump. Undefined without a splash made by the CLI.
   */
  logo: SplashLogo | undefined;
}

// hide() came: the splash may leave once the rest is done too.
let hideCalled = false;
let hideWaiter: (() => void) | undefined;

function hide() {
  hideCalled = true;
  hideWaiter?.();
}

let splash: Splash | undefined;

/**
 * The app's splash in React Native: lets it leave (`hide: 'manual'`), and gives the size of the native splash's logo
 * to start an animation from.
 *
 * @example
 * function Splash() {
 *   const { hide, logo } = useSplash();
 *   // … the logo at logo.width × logo.height in the middle of the window, then an animation that calls hide()
 * }
 */
export function useSplash(): Splash {
  splash ??= { hide, logo: getStartInfo().splashLogo ?? undefined };
  return splash;
}

/** Whether the splash shows at this start: the app has one, and the JavaScript runs for the first time in the process. */
export function showsSplash(config: SplashConfig | undefined): config is SplashConfig {
  return config !== undefined && getStartInfo().coldStart;
}

/**
 * The splash as the first root: at once, as it continues the native splash, over the whole window. Its own screen
 * options can't hold this, as `component` is any component.
 */
export const SPLASH_ROOT: LayoutRoot = {
  root: {
    component: {
      name: SPLASH_SCREEN,
      options: {
        layout: { safeAreaEdges: [] },
        animations: {
          setRoot: { enter: { enabled: false, waitForRender: true }, exit: { enabled: false } },
        },
      },
    },
  },
};

// The splash left, or the start ended without it.
let done = false;
// Ends the wait for the splash at once.
let release: (() => void) | undefined;

/**
 * Shows the splash and resolves once it may leave as far as it is concerned: it is on screen, `minDuration` passed
 * and, with `hide: 'manual'`, `hide()` came; or at once on `releaseSplash()`. A splash that failed to show holds
 * nothing: the native splash stays until the first root.
 */
export function showSplash(config: SplashConfig, show: () => Promise<unknown>): Promise<void> {
  const { minDuration = 0, hide: mode = 'auto' } = config;
  const hidden =
    mode === 'manual' && !hideCalled
      ? new Promise<void>((resolve) => {
          hideWaiter = resolve;
        })
      : undefined;
  const released = new Promise<void>((resolve) => {
    release = resolve;
  });
  const shown = show().then(
    () => Promise.all([delay(minDuration), hidden]).then(() => {}),
    (error) => {
      // A start that failed replaces the splash before it shows: its own error says enough.
      if (__DEV__ && !done) console.warn(`[navigation] splash: it didn't show (${String(error)})`);
    }
  );
  return Promise.race([shown, released]);
}

/**
 * The splash can't leave by itself any more, and the start goes on at once: its screen went with an Android activity
 * (the new one doesn't show it, and its setRoot may never end), or its component failed to render and can't call
 * `hide()`.
 */
export function releaseSplash() {
  release?.();
}

/** The splash left, or won't show: hide() does nothing from now on. */
export function splashDone() {
  done = true;
  hideCalled = true;
  hideWaiter = undefined;
  release = undefined;
}

function delay(ms: number): Promise<void> | undefined {
  return ms > 0 ? new Promise((resolve) => setTimeout(resolve, ms)) : undefined;
}
