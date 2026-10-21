import type { ComponentType } from 'react';
import type { NativeScreenReleasedEvent } from '../interfaces/Events';
import type { LayoutNode } from './layout';
import { commands as native, onNativeEvent } from './native';
import { forgetPlace } from './places';
import { forgetMergedOptions } from './rememberedOptions';
import { nativeScreenController } from './screens';

// The native screens shown with `nativeScreen`, by their ids. Their props go to the native side as plain data; the
// functions in them stay here, and the native side calls them by name. A screen with `useProps` has a hook that the
// hidden host runs while the screen is open: what it returns goes over the props of the command, the command waits for
// its first result, and each change after goes to the native side. The native side tells when a screen is gone.

/** What the hidden host renders for a native screen with `useProps`. */
export interface NativeControllerProps {
  componentId: string;
  /** The props of the command that showed the screen. */
  props: object;
  /** What the hook returned, after each render React committed. */
  onProps(props: object): void;
  /** The hook failed: a command still waiting for it fails. */
  onError(error: unknown): void;
}

/** The hook of a native screen, as the hidden host renders it. */
export interface NativeScreenHook extends NativeControllerProps {
  /** Tells apart two screens shown under one id, so the later one starts with a hook of its own. */
  key: string;
  Controller: ComponentType<NativeControllerProps>;
}

// The props of a screen as the native side reads them: the JSON of each data prop, and the functions by name.
interface NativeProps {
  data: Map<string, string>;
  functions: Map<string, (...args: unknown[]) => unknown>;
}

interface OpenScreen {
  id: string;
  name: string;
  props: NativeProps;
  /** The data the native side has, and the names of the functions; none before the command sends the screen. */
  sent?: { data: Map<string, string>; functions: string };
  hook?: NativeScreenHook;
  first?: Deferred;
}

interface Deferred {
  promise: Promise<void>;
  resolve(): void;
  reject(error: unknown): void;
}

// The host starts in milliseconds; past this a command fails with the reason instead of waiting for ever.
const HOST_TIMEOUT_MS = 5000;

const screens = new Map<string, OpenScreen>();
let shown = 0;
let hooks: readonly NativeScreenHook[] = [];
const listeners = new Set<() => void>();
let hostMounted = false;
const hostWaiters = new Set<() => void>();
let listening = false;

/** The hooks of the open native screens, for the hidden host. */
export const nativeScreenHooks = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  get: (): readonly NativeScreenHook[] => hooks,
};

/**
 * A native screen a command shows. Its props go into the node, the data apart from the names of the functions; a
 * screen with `useProps` starts its hook, and its props go in once the hook has returned (`whenNativeScreensReady`).
 */
export function showNativeScreen(node: LayoutNode, name: string, props: object): void {
  const screen: OpenScreen = { id: node.id, name, props: toNativeProps(props) };
  const Controller = nativeScreenController(name);
  if (Controller) {
    screen.first = deferred();
    screen.hook = {
      key: `${node.id}#${++shown}`,
      Controller,
      componentId: node.id,
      props,
      onProps: (result) => propsChanged(screen, { ...props, ...result }),
      onError: (error) => screen.first?.reject(error),
    };
  } else {
    writeProps(node, screen);
  }
  screens.set(node.id, screen);
  if (screen.hook) updateHooks();
}

/**
 * Sends a command once the native screens it shows have their props: those with `useProps` wait for the first result
 * of the hook, the others go at once. When the command fails, its native screens go.
 */
export function whenNativeScreensReady(
  nodes: LayoutNode[],
  send: () => Promise<string>
): Promise<string> {
  const shownScreens = externalNodes(nodes).flatMap((node) => {
    const screen = screens.get(node.id);
    return screen ? [{ node, screen }] : [];
  });
  if (shownScreens.length === 0) return send();
  const release = (error: unknown): never => {
    shownScreens.forEach(({ screen }) => forget(screen));
    throw error;
  };
  const waiting = shownScreens.filter(({ screen }) => screen.first && !screen.sent);
  if (waiting.length === 0) return send().catch(release);
  return startHost()
    .then(() => Promise.all(waiting.map(({ screen }) => screen.first!.promise)))
    .then(() => {
      // The latest props: a hook may have rendered again while another was not ready.
      waiting.forEach(({ node, screen }) => writeProps(node, screen));
      return send();
    })
    .catch(release);
}

/** Runs a function of the props of a native screen, which the native side called by its name. */
export function callNativeScreenFunction(id: string, name: string, args: unknown[]): unknown {
  const screen = screens.get(id);
  if (!screen) throw new Error(`[navigation] No native screen has the id '${id}'`);
  const fn = screen.props.functions.get(name);
  if (!fn) throw new Error(`[navigation] The props of '${screen.name}' have no function '${name}'`);
  return fn(...args);
}

/** The native screen with the id, for the navigation of its calls. */
export function nativeScreenInfo(id: string): { id: string; name: string } | undefined {
  const screen = screens.get(id);
  return screen && { id: screen.id, name: screen.name };
}

/** Gives the native side what its call came to: what it returned, as plain data, or the error's message. */
export function answerNativeScreenCall(callId: string, outcome: Promise<unknown>): void {
  outcome
    .then((value) => ({ value: JSON.parse(JSON.stringify(value) ?? 'null') }))
    .catch((error) => ({ error: error instanceof Error ? error.message : String(error) }))
    .then((result) => native.resolveNativeScreenCall(callId, result));
}

/** The hidden host renders, or is gone (a reload); the commands waiting for it go on. */
export function hostMountedChanged(mounted: boolean): void {
  hostMounted = mounted;
  if (!mounted) return;
  hostWaiters.forEach((started) => started());
  hostWaiters.clear();
}

/** Starts the hidden host that runs the hooks (in one tree it runs already); resolves once it renders. */
export function startHost(): Promise<void> {
  if (hostMounted) return Promise.resolve();
  native.startHost();
  return new Promise((resolve, reject) => {
    const started = () => {
      clearTimeout(timer);
      resolve();
    };
    const timer = setTimeout(() => {
      hostWaiters.delete(started);
      reject(new Error("[navigation] The host of the native screens' hooks did not start"));
    }, HOST_TIMEOUT_MS);
    hostWaiters.add(started);
  });
}

/** Listens to the native side letting its screens go, once for the app. */
export function listenToNativeScreens(): void {
  if (listening) return;
  listening = true;
  onNativeEvent<NativeScreenReleasedEvent>('NativeScreenReleased', ({ componentId }) => {
    forgetPlace(componentId);
    forgetMergedOptions(componentId);
    const screen = screens.get(componentId);
    if (screen) forget(screen);
  });
}

// What the hook returned: before the command sends the screen, it waits in the screen; after, the changes go.
function propsChanged(screen: OpenScreen, props: object) {
  if (screens.get(screen.id) !== screen) return;
  screen.props = toNativeProps(props);
  if (!screen.sent) {
    screen.first?.resolve();
    return;
  }
  const sent = screen.sent;
  const { data, functions } = screen.props;
  const changed: Record<string, unknown> = {};
  data.forEach((json, key) => {
    if (sent.data.get(key) !== json) changed[key] = JSON.parse(json);
  });
  const removed = [...sent.data.keys()].filter((key) => !data.has(key));
  const names = [...functions.keys()];
  const namesJson = JSON.stringify(names);
  if (Object.keys(changed).length === 0 && removed.length === 0 && namesJson === sent.functions) {
    return;
  }
  screen.sent = { data, functions: namesJson };
  native.updateNativeScreenProps(screen.id, { props: changed, removed, functions: names });
}

function writeProps(node: LayoutNode, screen: OpenScreen) {
  const { data, functions } = screen.props;
  node.data.passProps = Object.fromEntries([...data].map(([key, json]) => [key, JSON.parse(json)]));
  const names = [...functions.keys()];
  if (names.length > 0) node.data.functions = names;
  screen.sent = { data, functions: JSON.stringify(names) };
}

// Data goes as JSON, as the native side reads it: a date as its string, nothing for undefined.
function toNativeProps(props: object): NativeProps {
  const data = new Map<string, string>();
  const functions = new Map<string, (...args: unknown[]) => unknown>();
  for (const [key, value] of Object.entries(props)) {
    if (typeof value === 'function') {
      functions.set(key, value);
    } else {
      const json = JSON.stringify(value);
      if (json !== undefined) data.set(key, json);
    }
  }
  return { data, functions };
}

function forget(screen: OpenScreen) {
  if (screens.get(screen.id) !== screen) return;
  screens.delete(screen.id);
  if (screen.hook) updateHooks();
}

function updateHooks() {
  hooks = [...screens.values()].flatMap(({ hook }) => (hook ? [hook] : []));
  listeners.forEach((listener) => listener());
}

function externalNodes(nodes: LayoutNode[]): LayoutNode[] {
  return nodes.flatMap((node) =>
    node.type === 'ExternalComponent' ? [node] : externalNodes(node.children)
  );
}

function deferred(): Deferred {
  let resolve!: () => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<void>((onResolve, onReject) => {
    resolve = onResolve;
    reject = onReject;
  });
  // The command that waited has failed already; nobody waits again.
  promise.catch(() => {});
  return { promise, resolve, reject };
}
