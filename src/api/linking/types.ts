import type { RootLayout } from '../layout/Root';
import type { RootsMap, RootTarget } from '../app/roots';
import type { CheckedScreens } from '../types';

/** The params of a path pattern, each a string: `'user/:userId/:tab?'` gives `{ userId: string; tab?: string }`. */
export type LinkParams<Pattern extends string> = Simplify<
  { [Name in RequiredParam<Segment<Pattern>>]: string } & {
    [Name in OptionalParam<Segment<Pattern>>]?: string;
  }
>;

type Segment<Pattern extends string> = Pattern extends `${infer Head}/${infer Rest}`
  ? Head | Segment<Rest>
  : Pattern;
type RequiredParam<Part> = Part extends `:${string}?`
  ? never
  : Part extends `:${infer Name}`
    ? Name
    : never;
type OptionalParam<Part> = Part extends `:${infer Name}?` ? Name : never;
type Simplify<Value> = { [Key in keyof Value]: Value[Key] } & {};

/** A link as a route gets it. */
export interface LinkInfo {
  /** The whole URL. */
  url: string;
  /** The path the route matched, without the prefix and the query: `user/42`. */
  path: string;
  /** The query string by key; a key given twice keeps its last value. */
  query: Record<string, string>;
  /** Whether this link launched the app, or on Android a new activity of it. */
  initial: boolean;
}

/** What a route does in a running app: any commands. */
export type LinkHandler<Pattern extends string> = (
  params: LinkParams<Pattern>,
  link: LinkInfo
) => void;

/** A route with what it does when the link launches the app, when the app runs, and when it may run. */
export interface LinkRouteConfig<Pattern extends string, Context, Roots extends RootsMap> {
  /**
   * The first root when the link launches the app, in place of `root` / `initialRoot`: the screen of the link with the
   * screens to go back to, shown at once. A layout, or a root of `roots` (its name, or `[name, param]`). In a running
   * app, a route without `open` shows it with `navigation.switchTo`.
   */
  start?: (
    params: LinkParams<Pattern>,
    link: LinkInfo
  ) => RootTarget<Roots> | Exclude<RootLayout, string>;
  /**
   * What the link does in a running app, and when it launches the app and the route has no `start`: then it runs
   * once the first root is shown.
   */
  open?: LinkHandler<Pattern>;
  /** Whether the link may run now, in place of `when` of `linking`; while it is false the link waits. */
  when?: (context: Context) => boolean;
}

/**
 * The screens of the app that take the params of a pattern as their props, for a route given as a screen name.
 */
type LinkScreen<Pattern extends string, AppScreens> = {
  [Name in keyof AppScreens & string]: unknown extends CheckedScreens<
    { name: Name; props: LinkParams<Pattern> },
    AppScreens
  >
    ? Name
    : never;
}[keyof AppScreens & string];

/**
 * A route: a screen name, pushed with the params and the query as its props; a function, its `open`; or
 * `{ start, open, when }`.
 */
export type LinkRoute<Pattern extends string, Context, Roots extends RootsMap, AppScreens> =
  | NoInfer<LinkScreen<Pattern, AppScreens>>
  | LinkHandler<Pattern>
  | LinkRouteConfig<Pattern, Context, Roots>;

/** The links that open the app's screens: custom schemes, universal links (iOS) and App Links (Android). */
export interface LinkingConfig<
  Context = unknown,
  Roots extends RootsMap = RootsMap,
  Patterns extends string = string,
  AppScreens = Record<string, unknown>,
> {
  /**
   * The starts of the URLs the app takes: `'myapp://'`, `'https://example.com'`; what follows is the path. A URL that
   * starts with none is not the app's. Without it every URL goes: the host of an `http(s)` URL is left out, and for
   * another scheme the path starts after `://`.
   */
  prefixes?: string[];
  /**
   * The routes by path pattern: static parts, `:param`, and `:param?` at the end. The most specific match wins: more
   * required parts first, then a static part before a param at the same place, then fewer parts, then the order here.
   */
  routes: { [Pattern in Patterns]: LinkRoute<Pattern, Context, Roots, AppScreens> };
  /**
   * Whether a link may run now, from the app's context: `(context) => context.signedIn`. While it is false the link
   * doesn't run and waits; the latest one waiting is kept (`navigation.getPendingLink`). A route's own `when` goes
   * over it. Without it every link runs at once. It is asked when a link comes and, for a waiting one, on each
   * `setContext`: a `when` that reads a store (Redux, Zustand) instead isn't asked again when the store changes, so
   * use `pending: 'manual'` there.
   */
  when?: (context: Context) => boolean;
  /**
   * What a waiting link does once `when` lets it: `'auto'` runs it when the context changes so, after the commands then
   * on their way (`setContext` and `switchTo` of a sign-in); `'manual'` keeps it for `navigation.openPendingLink`,
   * which the app calls once it is ready, such as after a sign-in kept in a store. `'auto'` without it.
   */
  pending?: 'auto' | 'manual';
  /** A URL no route takes, or with none of `prefixes`. Without it a warning in development. */
  unmatched?: (url: string) => void;
}

/** A link that waits for `when` of `linking`. */
export interface PendingLink {
  /** The whole URL. */
  url: string;
  /** The path, without the prefix and the query. */
  path: string;
  /** The pattern of its route in `routes`. */
  route: string;
  /** The params of the path. */
  params: Record<string, string>;
  /** The query string by key. */
  query: Record<string, string>;
}
