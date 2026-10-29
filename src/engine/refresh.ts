// Fast Refresh runs an edited module again, and every component it exports is a new function. React Refresh keeps
// the versions of one component in a family: Metro registers each export under its module and name, and React renders
// the newest version of the family in place of the old one. What the library keeps of a screen (its options, its key,
// its pair) is read through the same family, so an edit reaches it without a reload.

/** The versions of one component React Refresh keeps, `current` the newest. */
interface Family {
  current: unknown;
}

interface ReactRefresh {
  getFamilyByType?: (type: unknown) => Family | undefined;
}

// React Native puts React Refresh where Metro's module system finds it, in development only.
function reactRefresh(): ReactRefresh | undefined {
  if (!__DEV__) return undefined;
  const global = globalThis as { __METRO_GLOBAL_PREFIX__?: string } & Record<string, unknown>;
  return global[`${global.__METRO_GLOBAL_PREFIX__ ?? ''}__ReactRefresh`] as
    ReactRefresh | undefined;
}

/** The family a component's versions are kept in; undefined in a release build or for a value Metro didn't export. */
export function familyOf(type: unknown): Family | undefined {
  return reactRefresh()?.getFamilyByType?.(type);
}

/**
 * The newest version of a component after Fast Refresh ran its module again; the component itself when there is none
 * newer, and in a release build.
 */
export function newestType<T extends object>(type: T): T {
  return (familyOf(type)?.current as T | undefined) ?? type;
}

let redefinedListener: ((screen: object) => void) | undefined;

/** Sets what runs when a module makes a screen again: Fast Refresh ran it after an edit. */
export function onRedefined(listener: (screen: object) => void): void {
  redefinedListener = listener;
}

/**
 * Tells about a screen a module made, once the module ran: Metro registers its exports after it, so the family of the
 * screen is known by then, and a new version of a screen that shows is found by it. Development only.
 */
export function redefined(screen: object): void {
  if (!__DEV__ || !redefinedListener) return;
  const listener = redefinedListener;
  Promise.resolve().then(() => listener(screen));
}
