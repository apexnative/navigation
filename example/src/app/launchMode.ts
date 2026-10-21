import { DevSettings, NativeModules, Platform, Settings } from 'react-native';

const ONE_TREE = 'showcase.oneTree';
const NATIVE_FIRST = 'showcase.nativeFirst';
const FAIL_START = 'showcase.failStart';

// Android keeps the mode in the app's preferences (ShowcaseSettings.kt of the Showcase).
const android = NativeModules.ShowcaseSettings as
  | {
      oneTree(): boolean;
      setOneTree(on: boolean): void;
      nativeFirst(): boolean;
      setNativeFirst(on: boolean): void;
      takeFailStart(): boolean;
      failNextStart(): void;
    }
  | undefined;

/**
 * The Showcase renders its screens in one React tree under `appProvider`, instead of a React root per screen. Read once
 * at launch: on iOS from the app's settings, on Android from its preferences; both keep it.
 */
export const oneTree =
  Platform.OS === 'ios' ? Boolean(Settings.get(ONE_TREE)) : Boolean(android?.oneTree());

/** Keeps the mode and reloads the app, which starts with it. */
export function setOneTree(on: boolean) {
  if (Platform.OS === 'android') {
    android?.setOneTree(on);
    return;
  }
  Settings.set({ [ONE_TREE]: on });
  DevSettings.reload();
}

/** The Showcase starts with a native screen as its first root (section 18.7). Read once at launch, as the mode. */
export const nativeFirst =
  Platform.OS === 'ios' ? Boolean(Settings.get(NATIVE_FIRST)) : Boolean(android?.nativeFirst());

/** Keeps whether to start with the native screen and reloads the app, which starts with it. */
export function setNativeFirst(on: boolean) {
  if (Platform.OS === 'android') {
    android?.setNativeFirst(on);
    return;
  }
  Settings.set({ [NATIVE_FIRST]: on });
  DevSettings.reload();
}

/** Whether this start fails (section 14.4); once: a new try goes on. Read in `willMount`. */
export function takeFailStart(): boolean {
  if (Platform.OS === 'android') return Boolean(android?.takeFailStart());
  const on = Boolean(Settings.get(FAIL_START));
  if (on) Settings.set({ [FAIL_START]: false });
  return on;
}

/** Makes the next start fail once and reloads the app. */
export function failNextStart() {
  if (Platform.OS === 'android') {
    android?.failNextStart();
    return;
  }
  Settings.set({ [FAIL_START]: true });
  DevSettings.reload();
}
