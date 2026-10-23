import React, { type ComponentType, useCallback, useEffect, useSyncExternalStore } from 'react';
import { AppRegistry } from 'react-native';
import type { Options } from '../interfaces/Options';
import { forgetPlace } from './places';
import { forgetProps, propsOf, watchProps } from './props';
import { forgetMergedOptions } from './rememberedOptions';
import { hasSlot } from './slots';
import type { NativeControllerProps } from './nativeScreens';

type OptionsOf = (props: object) => Options;

/** A pair of screens (`arrangement` of a screen, or `defineArrangement`), with every setting filled in. */
export interface EngineArrangement {
  primary: string;
  secondary: string;
  /** The props of the secondary from those of the show; without it the same props. */
  secondaryProps?: (props: object) => object;
  style: 'split' | 'overlay';
  axes: 'horizontal' | 'vertical' | 'both';
  fraction: number;
  primaryEdge: 'leading' | 'trailing' | 'top' | 'bottom';
  compact: 'primary' | 'pair';
}

/**
 * A screen of the app by its name: a React component, which gets its id with its props, a view the native side makes
 * under `nativeName`, or a pair of screens of its own (`defineArrangement`).
 */
export type EngineScreen =
  | {
      component: () => ComponentType<ScreenRootProps>;
      options: OptionsOf;
      /** The key of the screen by its props, if it has one: one screen per key in a stack. */
      key?: (props: object) => string | number | undefined;
      /** Loads the module of a lazy screen: undefined once it is loaded. */
      load?: () => Promise<void> | undefined;
      /** A React root of its own in the app's one tree too: the splash, which shows before `appProvider` may render. */
      ownRoot?: boolean;
      /** The pair the screen is the primary of, if it has a secondary: read when shown, as a lazy screen knows it then. */
      arrangement?: () => EngineArrangement | undefined;
    }
  | {
      nativeName: string;
      options: OptionsOf;
      /** Runs the screen's `useProps`. */
      controller?: ComponentType<NativeControllerProps>;
      /** The pair the screen is the primary of, if it has a secondary. */
      arrangement?: () => EngineArrangement | undefined;
    }
  | { pair: EngineArrangement };

/** What the root of a React screen renders: the screen's id, and the props the command gave it. */
export type ScreenRootProps = { componentId: string } & Record<string, unknown>;

const screens = new Map<string, EngineScreen>();
const roots = new Map<string, ComponentType<{ componentId: string; rootTag?: number }>>();
let unmounted: ((screenId: string) => void) | undefined;
// The React roots mounted under each id, by their root tags. One id can have several: after a JavaScript reload React
// Native starts the roots of the old screens again in the new runtime, under the ids the new screens get too, and
// unmounts them once the new ones show; a component of the default or a stack's options has one id and a root in the
// bar of every screen that shows it. The id lets its props go only with the last of them. A slot of the app's one tree
// has no tag: while the one tree shows a screen with the id, no root of that id lets it go.
const owners = new Map<string, Set<number>>();

/** What runs when the root of a screen unmounts: the screen is gone. */
export function onScreenUnmounted(listener: (screenId: string) => void): void {
  unmounted = listener;
}

/** Registers a screen; a React screen becomes a root React Native runs under the screen's name. */
export function registerScreen(name: string, screen: EngineScreen): void {
  screens.set(name, screen);
  roots.delete(name);
  if ('component' in screen) AppRegistry.registerComponent(name, () => screenRoot(name)!);
}

/**
 * Loads the modules of the lazy screens among the names. Undefined when none waits, so that a command goes on at once,
 * as it does without lazy screens.
 */
export function loadScreens(names: Iterable<string>): Promise<void> | undefined {
  // The screens of a pair entry load with it; the secondary of a screen is known once the screen is loaded.
  const named = [...names].flatMap((name) => {
    const screen = screens.get(name);
    return screen && 'pair' in screen ? [screen.pair.primary, screen.pair.secondary] : [name];
  });
  const secondaries = () => named.flatMap((name) => arrangementOf(name)?.secondary ?? []);
  const first = loadNames(named);
  return first ? first.then(() => loadNames(secondaries())) : loadNames(secondaries());
}

function loadNames(names: Iterable<string>): Promise<void> | undefined {
  const loads: Promise<void>[] = [];
  for (const name of new Set(names)) {
    const screen = screens.get(name);
    const loading = screen && 'component' in screen ? screen.load?.() : undefined;
    if (loading) loads.push(loading);
  }
  return loads.length > 0 ? Promise.all(loads).then(() => {}) : undefined;
}

/**
 * Runs once the lazy screens among the names are loaded: at once when none waits. What `run` throws rejects the
 * promise, as it does after a load.
 */
export function afterLoading<T>(names: Iterable<string>, run: () => Promise<T>): Promise<T> {
  const loading = loadScreens(names);
  if (loading) return loading.then(run);
  try {
    return run();
  } catch (error) {
    return Promise.reject(error);
  }
}

/** The key a screen goes to the native side with, its name in front: undefined for a screen without one. */
export function screenKey(name: string, props: object): string | undefined {
  const screen = screens.get(name);
  const key = screen && 'component' in screen ? screen.key?.(props) : undefined;
  return key === undefined ? undefined : `${name}:${key}`;
}

/** The options a screen shows with before those of the show; none for a name not registered. */
export function screenOptions(name: string, props: object): Options {
  const screen = screens.get(name);
  if (!screen) return {};
  // A pair entry has no options of its own: they are its primary's.
  return 'pair' in screen ? screenOptions(screen.pair.primary, props) : screen.options(props);
}

/** The pair a name shows: a pair entry, or a screen with a secondary (itself the primary); undefined otherwise. */
export function arrangementOf(name: string): EngineArrangement | undefined {
  const screen = screens.get(name);
  if (!screen) return undefined;
  return 'pair' in screen ? screen.pair : screen.arrangement?.();
}

/** Throws when the pair a name shows names a screen that is not in `screens`, or a pair entry. */
export function checkArrangement(name: string, pair: EngineArrangement): void {
  for (const part of [pair.primary, pair.secondary]) {
    const screen = screens.get(part);
    if (!screen) {
      throw new Error(
        `[navigation] The pair of '${name}' names '${part}', which is not in screens`
      );
    }
    if ('pair' in screen)
      throw new Error(`[navigation] The pair of '${name}' names the pair '${part}'`);
  }
}

/** Checks the pairs known when the screens are registered; a lazy screen's pair is checked when it shows. */
export function checkArrangements(): void {
  for (const [name, screen] of screens) {
    const pair =
      'pair' in screen
        ? screen.pair
        : 'load' in screen && screen.load
          ? undefined
          : screen.arrangement?.();
    if (pair) checkArrangement(name, pair);
  }
}

/** The name a native screen is registered under natively. */
export function nativeScreenName(name: string): string | undefined {
  const screen = screens.get(name);
  return screen && 'nativeName' in screen ? screen.nativeName : undefined;
}

/** What runs the `useProps` of a native screen; none for a screen without it. */
export function nativeScreenController(
  name: string
): ComponentType<NativeControllerProps> | undefined {
  const screen = screens.get(name);
  return screen && 'nativeName' in screen ? screen.controller : undefined;
}

/** Whether a native screen of the app has `useProps`: the host of the hooks then starts with the app. */
export function hasNativeScreenHooks(): boolean {
  return [...screens.values()].some((screen) => 'nativeName' in screen && !!screen.controller);
}

/** Whether a screen renders in a slot of the app's one tree, once the app has one: a React screen without its own root. */
export function rendersInSlot(name: string): boolean {
  const screen = screens.get(name);
  return screen !== undefined && 'component' in screen && !screen.ownRoot;
}

/** A React screen of the app, as opposed to a native screen or a name the app did not register. */
export function isReactScreen(name: string): boolean {
  const screen = screens.get(name);
  return screen !== undefined && 'component' in screen;
}

/**
 * The root a React screen runs in: the screen with the props a command left for it under its id, and the new ones when
 * options give its id new props. React Native gives a root only its id (and its own root props, which stay here); the
 * slot of the app's one tree renders the same root.
 */
export function screenRoot(
  name: string
): ComponentType<{ componentId: string; rootTag?: number }> | undefined {
  const screen = screens.get(name);
  if (!screen || !('component' in screen)) return undefined;
  let root = roots.get(name);
  if (!root) {
    const Screen = screen.component();
    root = function ScreenRoot(rootProps: { componentId: string; rootTag?: number }) {
      // In the app's one tree the slot renders the root, without a tag of its own.
      const { componentId, rootTag } = rootProps;
      const watch = useCallback(
        (watcher: () => void) => watchProps(componentId, watcher),
        [componentId]
      );
      const props = useSyncExternalStore(watch, () => propsOf(componentId));
      useEffect(() => {
        if (rootTag !== undefined) {
          const tags = owners.get(componentId) ?? new Set<number>();
          tags.add(rootTag);
          owners.set(componentId, tags);
        }
        return () => {
          // A root while another root of the id is mounted leaves the screen alone, and so does any root while the one
          // tree still has a slot with the id: a later screen under the id, the restarted root of a screen after a
          // reload into the one tree (its slot may mount only once the app's provider lets it), the screen rendering
          // again under the provider.
          const tags = owners.get(componentId);
          if (rootTag !== undefined && tags) {
            tags.delete(rootTag);
            if (tags.size > 0) return;
          }
          if (hasSlot(componentId)) return;
          owners.delete(componentId);
          forgetProps(componentId);
          forgetPlace(componentId);
          forgetMergedOptions(componentId);
          unmounted?.(componentId);
        };
      }, [componentId, rootTag]);
      return <Screen {...props} componentId={componentId} />;
    };
    root.displayName = `ScreenRoot(${name})`;
    roots.set(name, root);
  }
  return root;
}
