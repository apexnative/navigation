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
  register?: (type: unknown, id: string) => void;
  createSignatureFunctionForTransform?: () => (
    type: unknown,
    key: string,
    forceReset?: boolean
  ) => unknown;
}

// React Native puts React Refresh where Metro's module system finds it, in development only.
function reactRefresh(): ReactRefresh | undefined {
  if (!__DEV__) return undefined;
  const global = globalThis as { __METRO_GLOBAL_PREFIX__?: string } & Record<string, unknown>;
  return global[`${global.__METRO_GLOBAL_PREFIX__ ?? ''}__ReactRefresh`] as
    ReactRefresh | undefined;
}

/** Whether React Refresh runs: development with Metro. */
export function refreshRuns(): boolean {
  return reactRefresh()?.getFamilyByType !== undefined;
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

// The id each family of screens gives the components they render, so their versions join one family too.
const innerIds = new WeakMap<Family, string>();
let innerCount = 0;

/**
 * Puts a component a screen or the app renders (`owner`: the screen, or the app made by createNavigation; `role`: which
 * of its components) in a family of its own, once the owner's module ran and Metro has given the owner its family: the
 * next version of the owner registers its component under the same id, so React Refresh keeps the component's state
 * when the module runs again, also when the module doesn't export it. A component React Refresh knows already (its
 * module exports it, or Babel registered it) keeps its own family. Development only.
 */
export function keepStateOf(owner: object, component: unknown, role = 'component'): void {
  const refresh = reactRefresh();
  if (!refresh?.register || component === undefined) return;
  const register = refresh.register;
  Promise.resolve().then(() => {
    const family = familyOf(owner);
    if (!family) return;
    let id = innerIds.get(family);
    if (id === undefined) {
      id = `apexnative.navigation#${++innerCount}`;
      innerIds.set(family, id);
    }
    register(component, `${id}.${role}`);
  });
}

/**
 * Makes React Refresh mount a component of the library again, not keep its state, when a new version of it comes: its
 * hooks are the app's, which may have changed, and the library's code has no signature of them. Development only.
 */
export function resetOnEdit(type: object): void {
  reactRefresh()?.createSignatureFunctionForTransform?.()(type, 'apexnative.navigation', true);
}
