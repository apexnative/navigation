import type { LayoutRoot } from '../interfaces/Layout';
import type { Options } from '../interfaces/Options';

/**
 * What the native side needs again when it launches once more in the same JavaScript runtime: Android destroys the
 * activity with every screen while JavaScript lives on, then sends AppLaunched again.
 */
export const launchState = {
  /** The AppLaunched events received in this JavaScript runtime. */
  launch: 0,
  /** The launch the current root was set in. */
  rootLaunch: -1,
  /** The last root set, as it was passed: React screens keep their props, functions included. */
  root: undefined as LayoutRoot | undefined,
  /** The last default options set. */
  defaultOptions: undefined as Options | undefined,
};

/**
 * Whether a setRoot of this root sets the last root again for a launch of the native side after the one it was set in,
 * as a new activity needs it, rather than a root of the app's own.
 */
export function restoresRoot(layout: LayoutRoot): boolean {
  return layout === launchState.root && launchState.rootLaunch !== launchState.launch;
}

/** Every setRoot passes here, so a root set with the low-level API comes back too. */
export function rememberRoot(layout: LayoutRoot) {
  launchState.root = layout;
  launchState.rootLaunch = launchState.launch;
}

export function rememberDefaultOptions(options: Options) {
  launchState.defaultOptions = options;
}
