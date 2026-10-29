import React, {
  Component,
  type ComponentType,
  createContext,
  type ErrorInfo,
  lazy,
  type ReactNode,
  Suspense,
  useMemo,
  useState,
} from 'react';
import { DevSettings, Platform, StyleSheet } from 'react-native';
import ANNTurboModule from '../../adapters/NativeANNTurboModule';
import type { NativeControllerProps } from '../../engine/nativeScreens';
import { isButtonRoot } from '../../engine/props';
import {
  checkArrangements,
  onScreenUnmounted,
  registerScreen as registerEngineScreen,
  type ScreenRootProps,
  shownScreens,
} from '../../engine/screens';
import { screenUnmounted } from '../events/screenLifecycle';
import { mergeOptions } from '../../engine/mergeOptions';
import { preparedPadding } from '../../engine/slots';
import { clearOptions, mergeOptions as sendMergeOptions } from '../../engine/commands';
import { optionsChange } from '../../engine/optionsChange';
import { openNativeScreens } from '../../engine/nativeScreens';
import { propsOf } from '../../engine/props';
import { familyOf, newestType, onRedefined } from '../../engine/refresh';
import {
  buildScreenOptions,
  definitionOf,
  ownDefinitionOf,
  type ScreenDefinition,
  type ScreenOptionsInput,
} from './defineScreen';
import { isNativeScreen, type NativeScreen, type NativeScreenHookProps } from './nativeScreens';
import { type Arrangement, isArrangement, toEngineArrangement } from './arrangement';
import { type ErrorFallbackProps, ScreenBoundary } from './ScreenBoundary';
import ANNSafeAreaView from '../safeArea/ANNSafeAreaViewNativeComponent';

type ScreenComponent = ComponentType<any>;
type ScreenModule =
  ScreenComponent | ScreenDefinition | { default: ScreenComponent | ScreenDefinition };

/**
 * A screen: its component, its definition (`{ component, options }`, see `defineScreen`), `{ load }` to load its
 * module the first time a command shows it (`() => import('./screens/Settings')`, the screen is its default export),
 * or a screen of the native side (`nativeScreen`).
 */
export type ScreenEntry =
  | ScreenComponent
  | ScreenDefinition
  | { load: () => ScreenModule | PromiseLike<ScreenModule> }
  | NativeScreen<any>
  | Arrangement<any>;

export type Screens = Record<string, ScreenEntry>;

/** A screen: the id of this showing of it, and its name in `screens`. */
export interface ScreenInfo {
  /** The id of this showing: two screens pushed one over the other have the same name and different ids. */
  id: string;
  /** Its name in `screens`. */
  name: string;
}

/** Wraps every screen on its own. */
export type ScreenProvider = ComponentType<{ children: ReactNode; screen: ScreenInfo }>;

/** What a screen shows while it waits for a lazy component or data. */
export type LoadingFallback = ComponentType<{ screen: ScreenInfo }>;

/** What the app wraps each of its screens in. */
export interface ScreenWrapper {
  provider?: ScreenProvider;
  errorFallback?: ComponentType<ErrorFallbackProps>;
  loadingFallback?: LoadingFallback;
  /** An error of a screen's render, with the screen and React's component stack. */
  onError?: (error: unknown, screen: ScreenInfo, componentStack: string | null | undefined) => void;
}

export const ScreenContext = createContext<ScreenInfo | null>(null);

/** Screens sharing options, such as the sign-in screens without a top bar. */
export interface ScreenGroup {
  /** The options of every screen of the group; the screen's own options go over them. */
  options?: ScreenOptionsInput<object>;
  screens: Screens;
}

/** Screens to register: those of `screens`, or of a group. */
export interface ScreenSet {
  /** Where the screens come from, for the errors. */
  source: string;
  screens: Screens;
  options?: ScreenOptionsInput<object>;
  /** The screens render as React roots of their own, in the app's one tree too. */
  ownRoot?: boolean;
}

// The root of a screen tells when it unmounts: what the screen left behind goes.
onScreenUnmounted(screenUnmounted);

// The screens of defineScreen by name, in development: what `screens` gives (a lazy screen's module once loaded) and
// the options of its group.
const editable = new Map<
  string,
  { screen: () => unknown; groupOptions: ScreenOptionsInput<object> | undefined }
>();

onRedefined(applyEdit);

// Fast Refresh ran the module of a screen again: the screens of it that show take what the edit changed in their
// options, as a command would have shown them. The family's newest is still the version before the edit here, as React
// Refresh moves it on only after the update.
function applyEdit(next: object) {
  const family = familyOf(next);
  const edited = ownDefinitionOf(next) ?? (isNativeScreen(next) ? next : undefined);
  if (!family || !edited) return;
  for (const [name, { screen, groupOptions }] of editable) {
    const current = screen();
    if (current === undefined || familyOf(current) !== family) continue;
    const native = isNativeScreen(current);
    const before = native ? newestType(current) : definitionOf(current);
    // What a native screen is made of goes to the native side when it is registered and shown: a change of it starts
    // the app again.
    if (native && isNativeScreen(edited) && madeOtherwise(newestType(current), edited)) {
      DevSettings.reload('[navigation] A native screen changed its name or its useProps');
      return;
    }
    const shown = native
      ? openNativeScreens(name)
      : shownScreens(name).map((id) => ({ id, props: propsOf(id) }));
    for (const { id, props } of shown) {
      const options = (definition: { options?: ScreenOptionsInput<any> } | undefined) =>
        mergeOptions(
          buildScreenOptions(groupOptions, props),
          buildScreenOptions(definition?.options, props)
        );
      const { merge, removed } = optionsChange(options(before), options(edited));
      if (removed.length > 0) clearOptions(id, removed);
      if (merge) sendMergeOptions(id, merge);
    }
  }
}

function madeOtherwise(before: NativeScreen<any>, after: NativeScreen<any>): boolean {
  return before.nativeName !== after.nativeName || !before.useProps !== !after.useProps;
}

export function registerScreens(sets: ScreenSet[], wrapper: ScreenWrapper = {}) {
  // The screens of every set are in one namespace: a command names a screen without its group.
  const sources = new Map<string, string>();
  for (const { source, screens, options, ownRoot } of sets) {
    for (const [name, entry] of Object.entries(screens)) {
      const other = sources.get(name);
      if (other) {
        throw new Error(
          `[navigation] The screen '${name}' is in ${other} and in ${source}: a name goes once`
        );
      }
      sources.set(name, source);
      registerScreen(name, entry, wrapper, options, ownRoot);
    }
  }
  // A pair names screens of any set.
  checkArrangements();
}

function registerScreen(
  name: string,
  entry: ScreenEntry,
  wrapper: ScreenWrapper,
  groupOptions: ScreenOptionsInput<object> | undefined,
  ownRoot: boolean | undefined
) {
  if (isArrangement(entry)) {
    registerEngineScreen(name, {
      pair: toEngineArrangement(entry.definition.primary, entry.definition),
    });
    return;
  }
  if (isNativeScreen(entry)) {
    // Read on every show, the newest after Fast Refresh, as for a screen of defineScreen.
    const screen = () => newestType(entry);
    if (__DEV__) editable.set(name, { screen: () => entry, groupOptions });
    registerEngineScreen(name, {
      nativeName: entry.nativeName,
      options: (props) =>
        mergeOptions(
          buildScreenOptions(groupOptions, props),
          buildScreenOptions(screen().options, props)
        ),
      controller: entry.useProps && wrapNativeScreen(name, entry, wrapper),
      arrangement: () => {
        const arrangement = screen().arrangement;
        return arrangement && toEngineArrangement(name, arrangement);
      },
    });
    return;
  }
  // React.memo and forwardRef components are objects, so the kind of entry is told by its keys.
  if (isObject(entry) && 'load' in entry) {
    const screen = lazyScreen(entry.load);
    if (__DEV__) editable.set(name, { screen: screen.loaded, groupOptions });
    registerEngineScreen(name, {
      component: () => wrapScreen(name, screen.component, wrapper),
      // A command loads the screen before it reads its options.
      options: (props) =>
        mergeOptions(
          buildScreenOptions(groupOptions, props),
          buildScreenOptions(screen.definition()?.options, props)
        ),
      key: (props) => screen.definition()?.key?.(props),
      load: screen.load,
      ownRoot,
      arrangement: () => {
        const arrangement = screen.definition()?.arrangement;
        return arrangement && toEngineArrangement(name, arrangement);
      },
    });
    return;
  }
  // Read on every show: after Fast Refresh a screen of defineScreen gives its new options, key and pair.
  const definition = () => toDefinition(entry);
  if (__DEV__) editable.set(name, { screen: () => entry, groupOptions });
  registerEngineScreen(name, {
    component: () => wrapScreen(name, definition().component, wrapper),
    options: (props) =>
      mergeOptions(
        buildScreenOptions(groupOptions, props),
        buildScreenOptions(definition().options, props)
      ),
    key: (props) => definition().key?.(props),
    ownRoot,
    arrangement: () => {
      const arrangement = definition().arrangement;
      return arrangement && toEngineArrangement(name, arrangement);
    },
  });
}

function toDefinition(value: ScreenComponent | ScreenDefinition): ScreenDefinition {
  // A screen of defineScreen renders as itself: React Refresh knows it by its module's export, and the version of the
  // newest definition renders in its place.
  const defined = definitionOf(value);
  if (defined) return { ...defined, component: value as ScreenComponent };
  return isObject(value) && 'component' in value ? value : { component: value as ScreenComponent };
}

/**
 * A screen whose module loads the first time a command shows it. A failed load is tried again by the next command.
 * The component renders the loaded screen; one that renders before (a bar's component in the options of another
 * screen) waits for it in Suspense.
 */
function lazyScreen(loadModule: () => ScreenModule | PromiseLike<ScreenModule>) {
  // The screen the module gives; its definition is read from it each time, the newest after Fast Refresh.
  let loaded: ScreenComponent | ScreenDefinition | undefined;
  let loading: Promise<void> | undefined;
  const definition = () => (loaded ? toDefinition(loaded) : undefined);
  const load = (): Promise<void> | undefined => {
    if (loaded) return undefined;
    if (!loading) {
      loading = Promise.resolve()
        .then(loadModule)
        .then((module) => {
          loaded = unwrapModule(module);
        });
      loading.catch(() => {
        loading = undefined;
      });
    }
    return loading;
  };
  const Loaded = lazy(async () => {
    await load();
    return { default: definition()!.component };
  });
  function LazyScreen(props: object) {
    // The same component for the life of the view, so that the load doesn't mount it again.
    const [Screen] = useState(() => definition()?.component ?? Loaded);
    return <Screen {...props} />;
  }
  return { load, definition, loaded: () => loaded, component: LazyScreen };
}

function unwrapModule(loaded: ScreenModule): ScreenComponent | ScreenDefinition {
  return isObject(loaded) && 'default' in loaded ? loaded.default : loaded;
}

function isObject(value: unknown): value is object {
  return typeof value === 'object' && value !== null;
}

// Without the native side (tests with the mock) nothing lays the safe area view out: the content renders at once.
const WAITS_FOR_LAYOUT = ANNTurboModule != null;

function wrapScreen(name: string, Screen: ScreenComponent, wrapper: ScreenWrapper) {
  const { provider: Provider, errorFallback, loadingFallback: Loading, onError } = wrapper;
  // The component gets the props of the command, nothing else: its id comes from useScreen().
  function NavigationScreen({ componentId, ...props }: ScreenRootProps) {
    const screen = useMemo(() => ({ id: componentId, name }), [componentId]);
    // The content renders once the safe area view is laid out in its place, as react-native-safe-area-context does:
    // a list that is there before the first layout of the top bar makes UIKit start the large title collapsed. A
    // screen the native side laid out before its push (Android, one tree) knows its padding: it renders at once.
    const [initialPadding] = useState(() => preparedPadding(componentId));
    const [ready, setReady] = useState(!WAITS_FOR_LAYOUT || initialPadding !== undefined);
    // On iOS the native side only sizes a bar button's slot to the content and moves nothing inside it: the content is
    // centered here. Android's cell centers the component itself.
    const [barButton] = useState(() => Platform.OS === 'ios' && isButtonRoot(componentId));
    // One boundary inside the provider, so the fallback has what it provides, and one outside for the provider itself.
    const boundary = (children: ReactNode) => (
      <ScreenBoundary
        screen={screen}
        fallback={errorFallback}
        onError={onError && ((error, stack) => onError(error, screen, stack))}
      >
        {children}
      </ScreenBoundary>
    );
    const content = boundary(
      <Suspense fallback={Loading ? <Loading screen={screen} /> : null}>
        <Screen {...props} />
      </Suspense>
    );
    return (
      // Outside the providers, so the screen's navigation works inside them and in the fallbacks too.
      <ScreenContext.Provider value={screen}>
        {/* Keeps everything of the screen, the fallbacks too, out of its safe area on the edges of its options.
            box-none: an overlay lets the touches outside its content through. */}
        <ANNSafeAreaView
          screenId={componentId}
          initialPadding={initialPadding}
          onReady={() => setReady(true)}
          contentRendered={ready}
          pointerEvents="box-none"
          style={barButton ? [styles.safeArea, styles.barButton] : styles.safeArea}
        >
          {ready
            ? Provider
              ? boundary(<Provider screen={screen}>{content}</Provider>)
              : content
            : null}
        </ANNSafeAreaView>
      </ScreenContext.Provider>
    );
  }
  NavigationScreen.displayName = `Screen(${name})`;
  return NavigationScreen;
}

/**
 * The controller of a native screen's `useProps`: the hook in a component of its own, with the screen's navigation
 * and inside the screen provider, as a React screen has them. It renders nothing.
 */
function wrapNativeScreen(
  name: string,
  entry: NativeScreen<any>,
  { provider: Provider, onError }: ScreenWrapper
): ComponentType<NativeControllerProps> {
  // The screen's own component runs the hook: Fast Refresh puts a new version of it in place.
  const Hook = entry as unknown as ComponentType<NativeScreenHookProps<object>>;
  function NativeScreenProps({
    componentId,
    props,
    onProps,
    onError: failed,
  }: NativeControllerProps) {
    const screen = useMemo(() => ({ id: componentId, name }), [componentId]);
    const hook = <Hook props={props} onProps={onProps} />;
    return (
      <ScreenContext.Provider value={screen}>
        <HookBoundary
          onError={(error, componentStack) => {
            failed(error);
            onError?.(error, screen, componentStack);
          }}
        >
          {/* A hook that waits (Suspense) holds back its own command and nothing else. */}
          <Suspense fallback={null}>
            {Provider ? <Provider screen={screen}>{hook}</Provider> : hook}
          </Suspense>
        </HookBoundary>
      </ScreenContext.Provider>
    );
  }
  NativeScreenProps.displayName = `NativeScreenProps(${name})`;
  return NativeScreenProps;
}

// A hook that failed renders nothing again: the screen keeps the props it has.
class HookBoundary extends Component<
  {
    children: ReactNode;
    onError(error: unknown, componentStack: string | null | undefined): void;
  },
  { failed: boolean }
> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    this.props.onError(error, info.componentStack);
  }

  render() {
    return this.state.failed ? null : this.props.children;
  }
}

const styles = StyleSheet.create({
  safeArea: { flex: 1 },
  // Centered, not stretched: the content keeps its own size, so the slot still measures the content's width.
  barButton: { justifyContent: 'center', alignItems: 'center' },
});
