import type { ComponentType } from 'react';
import { setDefaultOptions, setRoot } from '../../engine/commands';
import { type AppProvider, enableSlots, withSlots } from '../../engine/slots';
import { listenToNativeScreens, startHost } from '../../engine/nativeScreens';
import { listenToSplitViews } from '../../engine/places';
import { hasNativeScreenHooks } from '../../engine/screens';
import type { LayoutRoot } from '../../interfaces/Layout';
import type { SafeAreaEdge, WindowOptions } from '../../interfaces/Options';
import type { Presentation } from '../commands/presentation';
import { addNavigationListeners, type NavigationListeners } from '../events/navigationEvents';
import { getNavigationState, listenToNavigationState } from '../state/navigationState';
import { watchSideMenus } from '../commands/sideMenus';
import { listenToNativeScreenCalls } from '../screens/nativeScreenCalls';
import { buildRoot, type RootLayout, withDefaultTransition } from '../layout/Root';
import {
  type LoadingFallback,
  registerScreens,
  type ScreenGroup,
  type ScreenInfo,
  type ScreenProvider,
  type Screens,
} from '../screens/registerScreens';
import type { ErrorFallbackProps } from '../screens/ScreenBoundary';
import { getAppContext, setAppContext } from '../context/appContext';
import {
  type AppOptionsInput,
  initTheme,
  type NavigationDirection,
  type NavigationTheme,
  themedDefaultOptions,
} from '../theme/theme';
import { setActiveApp } from './activeApp';
import { setStartFailure, START_FAILURE_SCREEN, startFailureScreen } from './startFailure';
import {
  releaseSplash,
  showsSplash,
  showSplash,
  SPLASH_ROOT,
  SPLASH_SCREEN,
  type SplashConfig,
  splashDone,
} from './splash';
import { launchState } from '../../engine/launchState';
import { watchBackListeners } from '../../engine/backListeners';
import { resolveRoot, type RootsMap, type RootTarget, type UsesOfRootValue } from './roots';
import {
  launchLink,
  launchRoot,
  linksRelaunched,
  linksStarted,
  reportLink,
  setupLinking,
} from '../linking/linking';
import type { LinkMatch } from '../linking/linkPaths';
import type { LinkingConfig } from '../linking/types';
import type { CheckedScreens } from '../types';

const NAVIGATION_APP = Symbol('apexnative.navigation.app');
declare const rootsType: unique symbol;
declare const screensType: unique symbol;

/** Where an error that goes to `onError` came from. */
export type NavigationErrorSource =
  /** A screen failed to render: it shows `errorFallback`, or nothing, and the other screens go on. */
  | { type: 'screen'; screen: ScreenInfo; componentStack?: string | null }
  /**
   * The app failed to start: `willMount` or the first root. `errorFallback` shows as the first screen, and its `retry`
   * starts again; without it the splash screen stays, and the next launch tries again.
   */
  | { type: 'start' }
  /** A route of `linking` failed: its `start`, its `open`, or a command they made. The app goes on. */
  | { type: 'link'; url: string };

/** The navigation made by `createNavigation`, for `AppRegistry.registerComponent`. */
export interface NavigationApp<Roots extends RootsMap = RootsMap, AppScreens = Screens> {
  readonly [NAVIGATION_APP]: true;
  /** Carries the type of the roots for `navigation.switchTo`; never set. */
  readonly [rootsType]?: Roots;
  /** Carries the type of the screens for the commands; never set. */
  readonly [screensType]?: AppScreens;
}

// What starts each app: AppRegistry calls it on every AppLaunched, and nothing else does.
const starts = new WeakMap<NavigationApp<any, any>, () => Promise<void>>();

/** Starts the app, or brings back its root when the native side launches again. */
export function startApp(app: NavigationApp<any, any>): Promise<void> {
  const start = starts.get(app);
  if (!start) throw new Error('[navigation] Not an app made by createNavigation');
  return start();
}

// The screens of each group by group name, for the types.
type ScreenGroups = Record<string, Screens>;

/** Every screen of the app: those of `screens` and of every group. */
type AllScreens<AppScreens, Groups extends ScreenGroups> = AppScreens &
  UnionToIntersection<Groups[keyof Groups]>;

type UnionToIntersection<Union> = (Union extends unknown ? (value: Union) => void : never) extends (
  value: infer Intersection
) => void
  ? Intersection
  : never;

interface BaseConfig<
  Context,
  AppScreens extends Screens,
  Groups extends ScreenGroups,
  Roots extends RootsMap = RootsMap,
  Patterns extends string = string,
> {
  /** The screens of the app by name. */
  screens?: AppScreens;
  /**
   * Screens sharing options, by group name. Their names are in one namespace with `screens`: a command names a
   * screen without its group.
   */
  groups?: { [Group in keyof Groups]: ScreenGroup & { screens: Groups[Group] } };
  /** What every push, modal and overlay starts from; the settings of a command go over it. */
  presentation?: Presentation;
  /** Handlers of the events of the navigation, for as long as the app runs. */
  listeners?: NavigationListeners;
  /**
   * Wraps the whole app once. With it every screen of `screens` and `groups` renders in one React tree inside it, so
   * what it holds (a store, a query client, a theme) is shared and lives as long as the app does.
   */
  appProvider?: AppProvider;
  /** Wraps every screen on its own, inside `appProvider`. */
  screenProvider?: ScreenProvider;
  /**
   * What a screen that failed to render shows instead, with `retry` to render it again; without it the screen is empty
   * and its top bar stays, so the user can go back. It renders inside `screenProvider`, unless that failed. When the
   * start fails, it is the first screen, in place of the splash screen, and its `retry` runs the start again from
   * `willMount`.
   */
  errorFallback?: ComponentType<ErrorFallbackProps>;
  /** What a screen shows while it waits for a lazy component or data (Suspense); nothing without it. */
  loadingFallback?: LoadingFallback;
  /**
   * The errors of the app: a screen that failed to render, a start that failed, and a link whose route failed. Without
   * it they go to the console, as React and React Native report them.
   */
  onError?: (error: unknown, source: NavigationErrorSource) => void;
  /**
   * The default options of every screen, over the colors of the theme: options, or a chain built from the theme and the
   * app's context (`(s, { theme, context }) => …`), which is built again when the theme changes.
   */
  options?: AppOptionsInput;
  /** The look of the navigation: colors of the bars, tabs and backgrounds; `navigation.setTheme` changes it. */
  theme?: NavigationTheme;
  /**
   * The edges on which every screen keeps its content out of its safe area. Without it: all four on Android; on iOS
   * every edge but those where the content goes under a bar that shows. A screen gives its own with `safeArea(...)`
   * in its options.
   */
  safeArea?: { edges: SafeAreaEdge[] };
  /** (iOS) The app's window, behind the screens: seen around a modal as it comes in. */
  window?: WindowOptions;
  /**
   * The direction of the app: `'rtl'`, `'ltr'`, or `'locale'` for the language of the app. The bars, the transitions and
   * the swipe back follow it, and follow `navigation.setDirection` at once (on iOS 15–16 a stack already shown keeps
   * its direction until its next push; on iOS 17+ a stack not on screen at the change does when the app had no
   * direction before). React content follows it in the screens created after a change
   * (React Native's `I18nManager`, which keeps it between launches); a reload of the JavaScript mirrors the screens
   * already open. The side menus keep their sides.
   */
  direction?: NavigationDirection;
  /**
   * The first value of the app's context, for every screen (`useAppContext`): there at once, while `willMount` runs
   * too. Its type is the type of the context. `navigation.setContext` is the one way to change it.
   */
  context?: Context;
  /**
   * Runs once per JavaScript runtime before the first root, while the splash screen shows. What the first root needs
   * goes into the context with `navigation.setContext`.
   */
  willMount?: () => void | PromiseLike<void>;
  /** Runs once per JavaScript runtime, once the first root is shown, with the context. */
  didMount?: (context: Context) => void;
  /**
   * The app's own splash in React Native: a component that shows after the native splash on a cold start of the app,
   * while `willMount` runs; the least time it stays, whether it waits for `useSplash().hide()`, and how the first root
   * replaces it. Once per process: a reload of the JavaScript goes to the first root at once.
   */
  splash?: SplashConfig;
  /**
   * The links that open the app's screens: custom schemes, universal links (iOS) and App Links (Android), by path. A
   * link runs after `willMount`, once the first root is shown; the link that launches the app can make that root itself
   * (`start`). The app declares its schemes and domains natively (Info.plist, entitlements, AndroidManifest).
   */
  linking?: LinkingConfig<Context, Roots, Patterns, AllScreens<AppScreens, Groups>>;
}

/** The first root: a layout, or a function making it from the context. */
type FirstRoot<Context, Layout extends RootLayout = RootLayout> =
  Layout | ((context: Context) => Layout);

/** One root: the app always starts with it. */
interface SingleRootConfig<Context, Root extends FirstRoot<Context> = FirstRoot<Context>> {
  /** The first root, from the context as `willMount` left it. */
  root: Root;
  roots?: never;
  initialRoot?: never;
}

/** Named roots to switch between, such as signed out and signed in. */
interface NamedRootsConfig<
  Context,
  Roots extends RootsMap,
  Target extends RootTarget<Roots> = RootTarget<Roots>,
> {
  /** The roots of the app by name: a layout, or a function making it from a param. */
  roots: Roots;
  /** The root to start with, from the context as `willMount` left it: its name, or `[name, param]`. */
  initialRoot: (context: Context) => Target;
  root?: never;
}

/** What `createNavigation` takes: `screens` and `root`, or `roots` with `initialRoot`; every other key is optional. */
export type NavigationConfig<
  Context = void,
  Roots extends RootsMap = Record<never, never>,
  Target extends RootTarget<Roots> = RootTarget<Roots>,
  AppScreens extends Screens = Record<never, never>,
  Groups extends ScreenGroups = Record<never, never>,
  Root extends FirstRoot<Context> = FirstRoot<Context>,
  Patterns extends string = string,
> = BaseConfig<Context, AppScreens, Groups, Roots, Patterns> &
  (SingleRootConfig<Context, Root> | NamedRootsConfig<Context, Roots, Target>);

/**
 * The roots, checked against the screens of the config itself: the app's registered type is made from this config,
 * so it can't check it.
 */
type CheckedRoots<Root, Roots, AppScreens> = {
  root?: CheckedScreens<UsesOfRootValue<Root>, AppScreens>;
  roots?: { [Name in keyof Roots]: CheckedScreens<UsesOfRootValue<Roots[Name]>, AppScreens> };
};

export function isNavigationApp(value: unknown): value is NavigationApp {
  return typeof value === 'object' && value !== null && starts.has(value as NavigationApp);
}

/**
 * The app's navigation in one object: its screens, its first root, how the screens look and show, the data every
 * screen reads, and what runs at launch. `AppRegistry.registerComponent` in `index.js` takes it, and the app starts
 * from it when the native side launches.
 *
 * @example
 * export const Navigation = createNavigation({
 *   screens: { Home: HomeScreen, Details: DetailsScreen },
 *   root: 'Home',
 * });
 */
export function createNavigation<
  Context = void,
  // As written: a screen named alone (`roots: { login: 'Login' }`) stays its name, not any string.
  const Roots extends RootsMap = Record<never, never>,
  const Target extends RootTarget<Roots> = RootTarget<Roots>,
  AppScreens extends Screens = Record<never, never>,
  Groups extends ScreenGroups = Record<never, never>,
  // The layout of the first root, not the whole value of `root`: TypeScript then types the parameter of
  // `root: (context) => …` from the context and infers the layout from what the function returns.
  const FirstLayout extends RootLayout = never,
  Patterns extends string = never,
>(
  config: NavigationConfig<
    Context,
    Roots,
    Target,
    AppScreens,
    Groups,
    FirstRoot<Context, FirstLayout>,
    Patterns
  > &
    NoInfer<CheckedRoots<FirstLayout, Roots, AllScreens<AppScreens, Groups>>>
): NavigationApp<Roots, AllScreens<AppScreens, Groups>> {
  // Counted from here: a "back again to leave" listener of the app's entry file comes after this call.
  watchBackListeners();
  // idle: not started yet, or the last start failed. mounting: the first root is on its way. mounted: it is shown.
  let phase: 'idle' | 'mounting' | 'mounted' = 'idle';
  let screensRegistered = false;
  // The splash shows at the first start of this runtime only: a start that failed and runs again goes without it.
  let splashChecked = false;
  let listening = false;
  let prepared: Promise<void> | undefined;
  let markMounted!: () => void;
  // A new activity came while the first root was on its way (Android): its link is taken once that root is shown.
  let relaunchedWhileMounting = false;
  // A first root whose activity went before it showed waits for the next one (Android).
  const relaunchWaiters: (() => void)[] = [];
  function relaunchedAfter(launch: number): Promise<void> {
    if (launchState.launch > launch) return Promise.resolve();
    return new Promise((resolve) => relaunchWaiters.push(resolve));
  }
  const mounted = new Promise<void>((resolve) => {
    markMounted = resolve;
  });

  // willMount runs once per JavaScript runtime; after a failure the next launch runs it again.
  function prepare(): Promise<void> {
    if (!prepared) {
      prepared = Promise.resolve().then(() => config.willMount?.());
      prepared.catch(() => {
        prepared = undefined;
      });
      if (__DEV__ && config.willMount) warnIfLong(prepared);
    }
    return prepared;
  }

  function firstRoot(context: Context): LayoutRoot {
    if (config.roots) {
      const { roots, initialRoot } = config as NamedRootsConfig<Context, Roots>;
      const target = initialRoot(context);
      const [name, ...param] = (Array.isArray(target) ? target : [target]) as [
        string,
        ...unknown[],
      ];
      return resolveRoot(roots, name, param);
    }
    const { root } = config as SingleRootConfig<Context>;
    return buildRoot(typeof root === 'function' ? root(context) : root);
  }

  async function mount() {
    phase = 'mounting';
    // The state before the first root: the links wait for a newer one, which tells the screen they act from.
    const stateBefore = getNavigationState();
    let link: LinkMatch | undefined;
    let linkHandled = false;
    try {
      if (!screensRegistered) {
        const wrapper = {
          errorFallback: config.errorFallback,
          loadingFallback: config.loadingFallback,
          onError:
            config.onError &&
            ((error: unknown, screen: ScreenInfo, componentStack: string | null | undefined) =>
              config.onError?.(error, { type: 'screen', screen, componentStack })),
        };
        registerScreens(
          [
            { source: 'screens', screens: config.screens ?? {} },
            ...Object.entries(config.groups ?? {}).map(([group, { screens, options }]) => ({
              source: `the group '${group}'`,
              screens,
              options,
            })),
            ...(config.errorFallback
              ? [
                  {
                    source: 'the start',
                    screens: { [START_FAILURE_SCREEN]: startFailureScreen(config.errorFallback) },
                  },
                ]
              : []),
          ],
          { ...wrapper, provider: config.screenProvider }
        );
        // The splash renders before willMount ends: a root of its own, without the app's providers, which may need what
        // willMount prepares.
        if (config.splash) {
          registerScreens(
            [
              {
                source: 'the splash',
                screens: { [SPLASH_SCREEN]: config.splash.component },
                ownRoot: true,
              },
            ],
            {
              ...wrapper,
              // A splash that failed to render can't call hide(): the start goes on without waiting for it.
              onError: (error: unknown, screen: ScreenInfo, componentStack: string | null | undefined) => {
                releaseSplash();
                wrapper.onError?.(error, screen, componentStack);
              },
            }
          );
        }
        screensRegistered = true;
      }
      // The link that launched the app is asked for first: on iOS the first React content to appear (the host below,
      // LogBox) would send it to Linking as a `url` event otherwise.
      const linkTaken = config.linking ? launchLink() : undefined;
      // The host of the native screens' hooks starts while willMount runs, so a native first screen doesn't wait for
      // it; a command that needs it reports a failure.
      if (hasNativeScreenHooks()) startHost().catch(() => {});
      // The app's splash, once, on a cold start: it shows while willMount and the launch link run.
      const splash = !splashChecked && showsSplash(config.splash) ? config.splash : undefined;
      splashChecked = true;
      // An empty id: the activity went before the splash showed (Android); it shows nothing until the next launch
      // releases it.
      const splashLeaves =
        splash &&
        showSplash(splash, () =>
          setRoot(SPLASH_ROOT).then((id) => (id === '' ? new Promise<never>(() => {}) : id))
        );
      await prepare();
      const context = getAppContext<Context>();
      // The link that launched the app makes the first root when its route has `start`; otherwise it runs after it.
      link = await linkTaken;
      const linked = launchRoot(link);
      linkHandled = linked.handled;
      // The first root replaces the splash once it may leave, with the splash's transition unless it has its own.
      await splashLeaves;
      let sent: { root: LayoutRoot; withTransition: LayoutRoot } | undefined;
      const afterSplash = (root: LayoutRoot) => {
        if (!splash) return root;
        sent = { root, withTransition: withDefaultTransition(root, splash.transition ?? 'fade') };
        return sent.withTransition;
      };
      const rootLaunch = launchState.launch;
      let rootId = linked.root && (await setLinkedRoot(afterSplash(linked.root), link!.url));
      rootId ??= await withSlots(() => setRoot(afterSplash(firstRoot(context))));
      // A new activity sets the root again as it was meant, without the splash's transition: there is no splash then.
      if (sent && launchState.root === sent.withTransition) launchState.root = sent.root;
      splashDone();
      // The activity can be replaced while willMount runs: the root then went to the old one. Android answers without
      // an id when the activity went before the root showed: the next activity sets it again, and the start waits for
      // that, still mounting so that the launch wakes it (what waits for the first root would find no screens).
      let sentIn = rootLaunch;
      for (let shown = rootId !== ''; ; shown = false) {
        if (!shown) await relaunchedAfter(sentIn);
        if ((await restoreRoot()) !== '') break;
        sentIn = launchState.rootLaunch;
      }
      phase = 'mounted';
      markMounted();
      config.didMount?.(context);
    } catch (error) {
      phase = 'idle';
      splashDone();
      if (config.errorFallback && screensRegistered) await showStartFailure(error);
      throw error;
    }
    // The links don't hold the start, and what they do is not the start's: a failing route goes to onError as a link.
    if (config.linking) {
      linksStarted(link, linkHandled, stateBefore).then(() =>
        relaunchedWhileMounting ? linksRelaunched(stateBefore) : undefined
      );
    }
  }

  // A root of `start` the native side can't set (a screen not in `screens`) is the link's failure: the usual first
  // root shows instead, and Try again isn't needed. The root's id, or undefined when it failed.
  async function setLinkedRoot(root: LayoutRoot, url: string): Promise<string | undefined> {
    try {
      return await withSlots(() => setRoot(root));
    } catch (error) {
      reportLink(error, url);
      return undefined;
    }
  }

  // A failed start shows errorFallback as the first root; a new try that fails too shows its error there.
  let failureShown = false;
  async function showStartFailure(error: unknown) {
    setStartFailure({
      error,
      retry: () => {
        if (phase === 'idle') reportStart(mount);
      },
    });
    if (failureShown) return;
    failureShown = true;
    try {
      await withSlots(() => setRoot({ root: { component: { name: START_FAILURE_SCREEN } } }));
    } catch {
      failureShown = false;
    }
  }

  // The errors of a start go to onError, or to React Native as errors of the app.
  async function reportStart(run: () => Promise<void>) {
    try {
      await run();
    } catch (error) {
      if (!config.onError) throw error;
      config.onError(error, { type: 'start' });
    }
  }

  async function launch() {
    setActiveApp({ roots: config.roots, presentation: config.presentation, mounted });
    if (!listening) {
      listening = true;
      // The edges of `safeArea` go under the app's options; the window's color goes with them, as the native side
      // takes it from the default options.
      initTheme(
        config.theme,
        {
          under: config.safeArea && { layout: { safeAreaEdges: config.safeArea.edges } },
          options: config.options,
          over: config.window && { window: config.window },
        },
        config.direction
      );
      if (config.context !== undefined) setAppContext(config.context);
      if (config.appProvider) enableSlots(config.appProvider);
      listenToNavigationState();
      watchSideMenus();
      listenToNativeScreens();
      listenToSplitViews();
      listenToNativeScreenCalls();
      if (config.listeners) addNavigationListeners(config.listeners);
    }
    launchState.launch++;
    // A new activity has no default options: the last ones set come back, else the theme's and the app's.
    const defaultOptions = launchState.defaultOptions ?? themedDefaultOptions();
    if (defaultOptions) setDefaultOptions(defaultOptions);

    if (phase === 'idle') return mount();
    if (phase === 'mounted') {
      const stateBefore = getNavigationState();
      await restoreRoot();
      if (config.linking) linksRelaunched(stateBefore);
      return;
    }
    // mounting: the first launch is still on its way and restores the root itself when it is done. The splash went
    // with the old activity and the new one doesn't show it: the start doesn't wait for it.
    relaunchedWhileMounting = true;
    releaseSplash();
    relaunchWaiters.splice(0).forEach((resume) => resume());
  }

  // The links listen from now on: one that comes before the native side launches waits for the first root.
  if (config.linking) {
    setupLinking(config.linking, {
      roots: config.roots,
      onError: config.onError && ((error, url) => config.onError?.(error, { type: 'link', url })),
    });
  }

  const app: NavigationApp<Roots, AllScreens<AppScreens, Groups>> = { [NAVIGATION_APP]: true };
  starts.set(app, () => reportStart(launch));
  return app;
}

// A willMount that never ends keeps the splash screen for good: in development the app hears of it.
function warnIfLong(willMount: Promise<void>) {
  const timer = setTimeout(
    () =>
      console.warn(
        '[navigation] willMount: still running after 10 s; the splash stays until it ends'
      ),
    10_000
  );
  const clear = () => clearTimeout(timer);
  willMount.then(clear, clear);
}

/**
 * Sets the last root again when it was set before this launch of the native side; the id of the last root it set, empty
 * when its activity went before it showed (Android), undefined when there was nothing to set.
 */
async function restoreRoot(): Promise<string | undefined> {
  let id: string | undefined;
  while (launchState.root && launchState.rootLaunch !== launchState.launch) {
    const root = launchState.root;
    id = await withSlots(() => setRoot(root));
  }
  return id;
}
