import { Linking, Platform } from 'react-native';
import { getLaunchLink } from '../../engine/native';
import type { LayoutRoot } from '../../interfaces/Layout';
import { resolveRoot, type RootsMap } from '../app/roots';
import { commandsStarted, whenCommandsSettle } from '../commands/errors';
import { getAppContext, subscribeAppContext } from '../context/appContext';
import { buildRoot, type RootLayout } from '../layout/Root';
import { navigation } from '../navigation';
import {
  getNavigationState,
  whenScreenAfter,
  type NavigationState,
} from '../state/navigationState';
import {
  compileRoutes,
  matchLink,
  parseLink,
  type CompiledRoute,
  type LinkMatch,
} from './linkPaths';
import { getPendingLink, setPendingLink, setPendingLinkOpener } from './pendingLinks';
import type { LinkInfo, LinkingConfig } from './types';

// The links of the app: the one that launched it, which can make the first root, and those that come while it runs.
// A link runs once the first root is shown and the native side told which screen shows, so its commands act from
// there; one whose `when` is false waits as the pending link. What the app's callbacks throw goes to `onError`.

type Route =
  | string
  | ((params: Record<string, string>, link: LinkInfo) => unknown)
  | {
      start?: (params: Record<string, string>, link: LinkInfo) => unknown;
      open?: (params: Record<string, string>, link: LinkInfo) => unknown;
      when?: (context: unknown) => boolean;
    };

// The routes as the app wrote them; each is read as a `Route`.
type Config = Omit<LinkingConfig<any, any, string, any>, 'routes'> & { routes: object };

let config: Config | undefined;
let roots: RootsMap | undefined;
let routes: CompiledRoute[] = [];
let onError: ((error: unknown, url: string) => void) | undefined;
// The links run at once. Before, they wait in `early`.
let started = false;
const early: { link: LinkMatch; initial: boolean }[] = [];
// The launch URL as the native side gave it, and matched: kept for every try of the first start.
let launchUrl: Promise<string | null> | undefined;
let launch: Promise<LinkMatch | undefined> | undefined;
let pendingCheck = false;
// The links whose `when` threw, reported once each.
const whenFailed = new Set<string>();

/** Starts the links of the app, once per JavaScript runtime. */
export function setupLinking(
  linking: Config,
  options: {
    roots: RootsMap | undefined;
    onError: ((error: unknown, url: string) => void) | undefined;
  }
): void {
  config = linking;
  roots = options.roots;
  onError = options.onError;
  // On iOS the first React content to appear sends the waiting links to Linking as `url` events; asked now, before
  // a warning can show LogBox, the launch link stays for the first root. Android reads it from the activity, which
  // is there once the native side launched.
  if (Platform.OS === 'ios') takeLaunchUrl();
  routes = compileRoutes(Object.keys(linking.routes));
  Linking.addEventListener('url', ({ url }) => receive(url));
  subscribeAppContext(openPendingWhenAllowed);
  setPendingLinkOpener((link) => run(link, routeOf(link), false));
}

/**
 * Takes the routes, the roots and `onError` of an app whose module ran again after an edit (Fast Refresh, development):
 * the links that come from now on find the new routes.
 */
export function updateLinking(
  linking: Config,
  options: {
    roots: RootsMap | undefined;
    onError: ((error: unknown, url: string) => void) | undefined;
  }
): void {
  config = linking;
  roots = options.roots;
  onError = options.onError;
  routes = compileRoutes(Object.keys(linking.routes));
}

/**
 * The link that launched the app, matched as the app starts (`unmatched` may run commands then); the same one for
 * every try of the first start.
 */
export function launchLink(): Promise<LinkMatch | undefined> {
  launch ??= takeLaunchUrl().then((url) => (url ? find(url) : undefined));
  return launch;
}

function takeLaunchUrl(): Promise<string | null> {
  // A link the native side can't read (a broken intent of another app) is no link: the start goes on.
  launchUrl ??= config ? getLaunchLink().catch(() => null) : Promise.resolve(null);
  return launchUrl;
}

/**
 * The first root the launch link makes with `start`, when its route has one and may run now. `handled`: the link
 * needs nothing more — it made the root, or its `start` failed and went to `onError`.
 */
export function launchRoot(link: LinkMatch | undefined): { root?: LayoutRoot; handled: boolean } {
  if (!link) return { handled: false };
  const route = routeOf(link);
  if (typeof route !== 'object' || !route.start || !allowed(route, link.url)) {
    return { handled: false };
  }
  try {
    return { root: toRoot(route.start(link.params, infoOf(link, true))), handled: true };
  } catch (error) {
    report(error, link.url);
    return { handled: true };
  }
}

/** A link that failed outside its route: the native side couldn't set the root its `start` made. */
export function reportLink(error: unknown, url: string): void {
  report(error, url);
}

/**
 * The first root is shown: once the native side told its screen, the launch link runs unless it was handled, then the
 * links that came meanwhile.
 */
export async function linksStarted(
  link: LinkMatch | undefined,
  handled: boolean,
  stateBefore: NavigationState
): Promise<void> {
  // A first frame can take long on a slow device: the limit only keeps a lost state from holding the links.
  await whenScreenAfter(stateBefore, 3000);
  started = true;
  if (link && !handled) handle(link, true);
  early.splice(0).forEach(({ link: next, initial }) => handle(next, initial));
}

/** The native side launched again (Android: a new activity): its link runs as in a running app. */
export async function linksRelaunched(stateBefore: NavigationState): Promise<void> {
  const url = await getLaunchLink().catch(() => null);
  const link = url ? find(url) : undefined;
  if (!link) return;
  await whenScreenAfter(stateBefore, 3000);
  handle(link, true);
}

function receive(url: string): void {
  const link = find(url);
  if (!link) return;
  if (started) handle(link, false);
  else early.push({ link, initial: false });
}

function find(url: string): LinkMatch | undefined {
  const parsed = parseLink(url, config?.prefixes);
  const link = parsed && matchLink(parsed, routes);
  if (link) return link;
  try {
    if (config?.unmatched) config.unmatched(url);
    else if (__DEV__) console.warn(`[navigation] No route of linking takes '${url}'`);
  } catch (error) {
    report(error, url);
  }
  return undefined;
}

function routeOf(link: { route: string }): Route {
  return (config!.routes as Record<string, Route>)[link.route]!;
}

// A `when` that throws keeps the link waiting; its error is reported once for the link.
function allowed(route: Route, url: string): boolean {
  const when = (typeof route === 'object' && route.when) || config?.when;
  try {
    return !when || when(getAppContext());
  } catch (error) {
    if (!whenFailed.has(url)) {
      whenFailed.add(url);
      report(error, url);
    }
    return false;
  }
}

function handle(link: LinkMatch, initial: boolean): void {
  const route = routeOf(link);
  if (allowed(route, link.url)) {
    run(link, route, initial);
    return;
  }
  const { url, path, route: pattern, params, query } = link;
  setPendingLink({ url, path, route: pattern, params, query });
}

/** Runs a route as in a running app: `open`, the screen pushed, or `start` shown with `switchTo`. */
async function run(link: LinkMatch, route: Route, initial: boolean): Promise<void> {
  const info = infoOf(link, initial);
  try {
    if (typeof route === 'string') {
      await (navigation.push as (name: string, config: object) => Promise<string>)(route, {
        props: { ...link.query, ...link.params },
      });
    } else if (typeof route === 'function') {
      await route(link.params, info);
    } else if (route.open) {
      await route.open(link.params, info);
    } else if (route.start) {
      await switchToTarget(route.start(link.params, info));
    }
  } catch (error) {
    report(error, link.url);
  }
}

function switchToTarget(target: unknown): Promise<void> {
  const switchTo = navigation.switchTo as (target: unknown, ...param: unknown[]) => Promise<void>;
  if (Array.isArray(target)) return switchTo(target[0], ...target.slice(1));
  return switchTo(target);
}

function toRoot(target: unknown): LayoutRoot {
  if (typeof target === 'string') return resolveRoot(roots, target, []);
  if (Array.isArray(target)) return resolveRoot(roots, target[0] as string, target.slice(1));
  return buildRoot(target as RootLayout);
}

function infoOf(link: LinkMatch, initial: boolean): LinkInfo {
  return { url: link.url, path: link.path, query: link.query, initial };
}

function report(error: unknown, url: string): void {
  if (onError) onError(error, url);
  else console.error(`[navigation] The link '${url}' failed`, error);
}

// A waiting link runs once the context lets it, after the commands of the same change — the `switchTo` that follows
// `setContext` at a sign-in — and after the native side told the screen they show, so the link opens in the new root.
function openPendingWhenAllowed(): void {
  if (config?.pending === 'manual' || pendingCheck || !getPendingLink()) return;
  pendingCheck = true;
  const stateBefore = getNavigationState();
  const commandsBefore = commandsStarted();
  setTimeout(async () => {
    try {
      await whenCommandsSettle(commandsBefore);
      if (commandsStarted() !== commandsBefore) await whenScreenAfter(stateBefore, 1000);
    } finally {
      pendingCheck = false;
    }
    const link = getPendingLink();
    if (!link || !allowed(routeOf(link), link.url)) return;
    setPendingLink(null);
    run({ ...link }, routeOf(link), false);
  }, 0);
}
