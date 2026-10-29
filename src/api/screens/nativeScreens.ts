import { useEffect } from 'react';
import { redefined, resetOnEdit } from '../../engine/refresh';
import type { ScreenArrangement } from './arrangement';
import type { ScreenOptionsInput } from './defineScreen';

const NATIVE_SCREEN = Symbol('apexnative.navigation.nativeScreen');

/** A screen made on the native side (SwiftUI, UIKit, Compose, Android views), registered there under `nativeName`. */
export interface NativeScreen<Props extends object = {}> {
  readonly [NATIVE_SCREEN]: true;
  readonly nativeName: string;
  readonly options?: ScreenOptionsInput<Props>;
  readonly useProps?: (props: Props) => object;
  readonly arrangement?: ScreenArrangement<Props>;
  /** Carries the type of the props; never set. */
  readonly props?: Props;
}

/** How a native screen shows: its options, and the hook that gives it the app's data. */
export interface NativeScreenDefinition<Props extends object> {
  options?: ScreenOptionsInput<Props>;
  /**
   * Gives the screen props from the app while it is open: data of the stores and of the app's context, functions the
   * native side calls (`dispatch`, the API client). A hook: it runs in a component of its own, inside the app's
   * providers and with the navigation of the screen, so any hook works in it. What it returns goes over the props of
   * the command; the command shows the screen once it has returned, and every change goes to the native side.
   */
  useProps?: (props: Props) => object;
  /** A second screen beside or under this one in a large window, as `arrangement` of `defineScreen`. */
  arrangement?: ScreenArrangement<Props>;
}

/**
 * A native screen for `screens`: commands and layouts name it like any other screen, and its props go to the native
 * creator. `nativeName` is the name it is registered under natively (`ApexNavigation.registerNativeScreen`). It is a
 * component that runs `useProps`, so a file that exports only its screens updates by Fast Refresh, the hook and the
 * options too.
 *
 * @example
 * const Settings = nativeScreen('Settings', {
 *   useProps: () => {
 *     const notifications = useSelector(selectNotifications);
 *     const dispatch = useDispatch();
 *     return {
 *       notifications,
 *       onNotificationsChange: (on: boolean) =>
 *         dispatch(setNotifications(on)),
 *     };
 *   },
 * });
 */
export function nativeScreen<Props extends object = {}>(
  nativeName: string,
  definition: NativeScreenDefinition<Props> = {}
): NativeScreen<Props> {
  const { options, useProps = noProps, arrangement } = definition;
  // A component that runs the hook, so that a file exporting only its screens stays a boundary of Fast Refresh: React
  // Refresh puts the new version in place, with the edited hook.
  function NativeScreen({ props, onProps }: NativeScreenHookProps<Props>) {
    const result = useProps(props);
    // After each render React committed, so the native side never shows what the app did not.
    useEffect(() => onProps(result));
    return null;
  }
  const screen = Object.assign(NativeScreen, {
    [NATIVE_SCREEN]: true as const,
    nativeName,
    options,
    useProps: definition.useProps,
    arrangement,
  });
  // The hook is the app's and may have other hooks after an edit: its new version mounts anew.
  resetOnEdit(screen);
  redefined(screen);
  return screen;
}

function noProps(): object {
  return {};
}

/** What the component of a native screen renders with: the props of the command, and where the hook's result goes. */
export interface NativeScreenHookProps<Props> {
  props: Props;
  onProps(result: object): void;
}

export function isNativeScreen(value: unknown): value is NativeScreen<any> {
  return (
    (typeof value === 'function' || typeof value === 'object') &&
    value !== null &&
    NATIVE_SCREEN in value
  );
}
