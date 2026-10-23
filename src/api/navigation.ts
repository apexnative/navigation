import { setRoot } from '../engine/commands';
import { withSlots } from '../engine/slots';
import { getActiveApp } from './app/activeApp';
import { resolveRoot, type RootParam } from './app/roots';
import * as commands from './commands/commands';
import type { SplitColumn } from './commands/commands';
import { command } from './commands/errors';
import { closeMenu, openMenu } from './commands/sideMenus';
import type { ColumnConfig, ModalConfig, OverlayConfig, PushConfig } from './commands/presentation';
import {
  type NavigationEventName,
  type NavigationEvents,
  onNavigationEvent,
} from './events/navigationEvents';
import { buildRoot, type RootLayout, type UsesOfRoot } from './layout/Root';
import { getNavigationState, type NavigationState, whenTabShows } from './state/navigationState';
import type { StackEntry } from './layout/Stack';
import type { UsesOf } from './layout/element';
import { getAppContext, setAppContext } from './context/appContext';
import { getPendingLink, openPendingLink, setPendingLink } from './linking/pendingLinks';
import type { PendingLink } from './linking/types';
import {
  type NavigationDirection,
  type NavigationTheme,
  setDirection,
  setTheme,
} from './theme/theme';
import type {
  CheckedScreens,
  RegisteredContext,
  RegisteredRoots,
  RegisteredScreens,
  ScreenName,
  ShowArgs,
} from './types';

type RootName = keyof RegisteredRoots & string;

/** The screens of a stack, each checked against the screens of the app. */
export type CheckedEntries<Entries extends readonly StackEntry[]> = {
  [Index in keyof Entries]: Entries[Index] &
    NoInfer<CheckedScreens<UsesOf<Entries[Index]>, RegisteredScreens>>;
};

export interface AppNavigation {
  /** Replaces the whole navigation with a root of `roots`: the modals close, the overlays stay. */
  switchTo<Name extends RootName>(
    name: Name,
    ...param: RootParam<RegisteredRoots[Name]>
  ): Promise<void>;
  /**
   * Replaces the whole navigation with a root of its own, such as the screens of a deep link. A screen named alone
   * is a root name here, so it goes as `Stack('Name')`.
   */
  switchTo<const Layout extends Exclude<RootLayout, string>>(
    layout: Layout & NoInfer<CheckedScreens<UsesOfRoot<Layout>, RegisteredScreens>>
  ): Promise<void>;
  /**
   * Shows a screen in the stack of the screen the user sees; resolves with the id of the screen shown (for a pair, the
   * pair's id).
   */
  push<Name extends ScreenName>(name: Name, ...args: ShowArgs<Name, PushConfig>): Promise<string>;
  /** Goes back in the stack of the screen the user sees; see `useNavigation().pop`. */
  pop(): Promise<string>;
  /** Goes back to a screen of its stack, by its id. */
  popTo(screenId: string): Promise<string>;
  /** `pop()` has a screen to go back to from the one the user sees; see `useNavigation().canPop`. Answers at once. */
  canPop(): boolean;
  /** The screen the user sees is in a modal or an overlay. Answers at once. */
  canDismiss(): boolean;
  /** Shows a column of the split view the user is in; see `useNavigation().showColumn`. */
  showColumn(column: SplitColumn): Promise<void>;
  /** Hides a column of the split view the user is in. */
  hideColumn(column: SplitColumn): Promise<void>;
  /** Opens a menu of the side menu the user is in; resolves once it is open and `getState()` shows its screen. */
  openMenu(side: 'left' | 'right'): Promise<void>;
  /**
   * Closes the menu that is open; resolves once it is closed and `getState()` shows the center, so the next command
   * acts from there.
   */
  closeMenu(): Promise<void>;
  /** What the back button would do on the screen the user sees: `pop()`, or `dismiss()` of its modal. */
  goBack(): Promise<string>;
  /** `goBack()` has something to do. Answers at once. */
  canGoBack(): boolean;
  /** Goes back to the first screen of the stack of the screen the user sees. */
  popToRoot(): Promise<string>;
  /** Shows a screen alone in the stack of the screen the user sees, in place of its history; see `useNavigation().replace`. */
  replace<Name extends ScreenName>(
    name: Name,
    ...args: ShowArgs<Name, PushConfig>
  ): Promise<string>;
  /** Replaces the screens of the stack of the screen the user sees. */
  setStack<const Entries extends readonly StackEntry[]>(
    ...entries: CheckedEntries<Entries>
  ): Promise<string>;
  /** Shows a screen in a column of the split view of the screen the user sees; see `useNavigation().showInColumn`. */
  showInColumn<Name extends ScreenName>(
    name: Name,
    ...args: ShowArgs<Name, ColumnConfig>
  ): Promise<string>;
  /** Shows a screen over everything, in a stack of its own unless `stack: false`. */
  showModal<Name extends ScreenName>(
    name: Name,
    ...args: ShowArgs<Name, ModalConfig>
  ): Promise<string>;
  /** Dismisses a modal by its id, or the modal a screen with this id is in. */
  dismissModal(modalId: string): Promise<string>;
  dismissAllModals(): Promise<string>;
  /** Shows a screen over everything, without taking the place of any. */
  showOverlay<Name extends ScreenName>(
    name: Name,
    ...args: ShowArgs<Name, OverlayConfig>
  ): Promise<string>;
  /**
   * Dismisses an overlay by its id. iOS also takes the id of a screen inside the overlay; Android takes only the
   * overlay's own id and rejects the id of a screen inside it.
   */
  dismissOverlay(overlayId: string): Promise<string>;
  dismissAllOverlays(): Promise<string>;
  /**
   * Selects a tab of the tabs of the screen the user sees: by its index, or by the id of the tab. Resolves once the
   * state shows it, so the next command acts in that tab. The state has the tab of the root's tabs only: in tabs over
   * them (tabs in a modal) it resolves on the next change of the state, at the latest after 300 ms, and at once when
   * the root's tabs show a tab with that index.
   */
  selectTab(tab: number | string): Promise<void>;
  /**
   * The badge of the tab with the id, or of the tab of the screen the user sees: a count or a short text. `null` and the
   * number `0` take it away, so a count of unread messages goes as it is; the text `'0'` shows "0".
   */
  setTabBadge(badge: string | number | null, tab?: string): Promise<void>;
  /** Changes the theme of the navigation: the screens shown take it too, `useNavigationTheme` renders again. */
  setTheme(theme: NavigationTheme): void;
  /**
   * Changes the direction of the app (`direction` of createNavigation): the bars, the transitions and the swipe back
   * follow at once, React content in the screens created after it. On iOS 15–16 a stack already shown keeps its
   * direction (its bar, its swipe back and its transitions) until its next push; on iOS 17+ so does a stack that isn't
   * on screen at the change (another tab, under a full-screen modal) when the app had no direction before.
   */
  setDirection(direction: NavigationDirection): void;
  /** The app's context: `context` of createNavigation, as `setContext` changed it since. */
  getContext(): RegisteredContext;
  /** Changes the app's context: every `useAppContext` renders again. */
  setContext(next: RegisteredContext | ((previous: RegisteredContext) => RegisteredContext)): void;
  /**
   * What the user sees now: the screen and the selected tab. Once a command resolves, it shows what the command did.
   */
  getState(): NavigationState;
  /** The link that waits for `when` of `linking`, the latest one; null when none waits. */
  getPendingLink(): PendingLink | null;
  /**
   * Runs the waiting link now, as in a running app, whatever its `when` says, and forgets it. Resolves once its route
   * has run, with false when no link waits; a route that fails goes to `onError`.
   */
  openPendingLink(): Promise<boolean>;
  /** Forgets the waiting link. */
  clearPendingLink(): void;
  /** Listens to an event of the navigation, outside the screens too; returns the function that stops listening. */
  on<Name extends NavigationEventName>(
    name: Name,
    handler: (event: NavigationEvents[Name]) => void
  ): () => void;
}

/** The started app; a command made while it starts comes after its first root. */
async function startedApp() {
  const app = getActiveApp();
  if (!app) {
    throw new Error(
      '[navigation] A command before the app started: register it with AppRegistry.registerComponent'
    );
  }
  await app.mounted;
  return app;
}

function switchTo(target: RootLayout, ...param: unknown[]): Promise<void> {
  return command('switchTo', async () => {
    const app = await startedApp();
    const root =
      typeof target === 'string' ? resolveRoot(app.roots, target, param) : buildRoot(target);
    await withSlots(() => setRoot(root));
  });
}

/** The screen the user sees, which the stack commands outside the screens act from. */
async function currentScreen(): Promise<string> {
  await startedApp();
  const screen = getNavigationState().screen;
  if (!screen) throw new Error('[navigation] No screen shows, so there is no stack to act on');
  return screen.id;
}

/** A check of the screen the user sees; false while none shows. */
function seenScreen(check: (screenId: string) => boolean): boolean {
  const screen = getNavigationState().screen;
  return screen ? check(screen.id) : false;
}

/** A command from the screen the user sees; an error says which command it was. */
function fromCurrent<T>(name: string, run: (screenId: string) => Promise<T>): Promise<T> {
  return command(name, async () => run(await currentScreen()));
}

/** A command that needs no screen, only the app started: made while it starts, it comes after the first root. */
function afterStart<T>(name: string, run: () => Promise<T>): Promise<T> {
  return command(name, async () => {
    await startedApp();
    return run();
  });
}

/** The navigation of the app, for anywhere, outside the screens too. */
export const navigation: AppNavigation = {
  switchTo,
  push: ((name: string, config?: PushConfig) =>
    fromCurrent('push', (id) => commands.push(id, name, config))) as AppNavigation['push'],
  pop: () => fromCurrent('pop', (id) => commands.pop(id)),
  popTo: (screenId) => afterStart('popTo', () => commands.popTo(screenId)),
  popToRoot: () => fromCurrent('popToRoot', (id) => commands.popToRoot(id)),
  replace: ((name: string, config?: PushConfig) =>
    fromCurrent('replace', (id) => commands.replace(id, name, config))) as AppNavigation['replace'],
  canPop: () => seenScreen((id) => commands.canPop(id)),
  canDismiss: () => seenScreen((id) => commands.canDismiss(id)),
  goBack: () => fromCurrent('goBack', (id) => commands.goBack(id)),
  showColumn: (column) =>
    fromCurrent('showColumn', (id) => commands.setColumnShown(id, column, true)),
  hideColumn: (column) =>
    fromCurrent('hideColumn', (id) => commands.setColumnShown(id, column, false)),
  openMenu: (side) => fromCurrent('openMenu', (id) => openMenu(id, side)),
  closeMenu: () => fromCurrent('closeMenu', (id) => closeMenu(id)),
  canGoBack: () => seenScreen((id) => commands.canPop(id) || commands.canDismiss(id)),
  setStack: ((...entries: StackEntry[]) =>
    fromCurrent('setStack', (id) => commands.setStack(id, entries))) as AppNavigation['setStack'],
  showInColumn: ((name: string, config?: ColumnConfig) =>
    fromCurrent('showInColumn', (id) =>
      commands.showInColumn(id, name, config)
    )) as AppNavigation['showInColumn'],
  showModal: ((name: string, config?: ModalConfig) =>
    afterStart('showModal', () => commands.showModal(name, config))) as AppNavigation['showModal'],
  dismissModal: (modalId) => afterStart('dismissModal', () => commands.dismissModal(modalId)),
  dismissAllModals: () => afterStart('dismissAllModals', () => commands.dismissAllModals()),
  showOverlay: ((name: string, config?: OverlayConfig) =>
    afterStart('showOverlay', () =>
      commands.showOverlay(name, config)
    )) as AppNavigation['showOverlay'],
  dismissOverlay: (overlayId) =>
    afterStart('dismissOverlay', () => commands.dismissOverlay(overlayId)),
  dismissAllOverlays: () => afterStart('dismissAllOverlays', () => commands.dismissAllOverlays()),
  // Resolves once the state shows the tab, so the next command acts in it.
  selectTab: (tab) =>
    fromCurrent('selectTab', async (id) => {
      const shown = whenTabShows(tab);
      commands.selectTab(id, tab);
      await shown;
    }),
  // A tab with an id needs no screen, only a started app.
  setTabBadge: (badge, tab) =>
    tab
      ? afterStart('setTabBadge', async () => commands.setTabBadge(tab, badge, tab))
      : fromCurrent('setTabBadge', async (id) => commands.setTabBadge(id, badge)),
  setTheme,
  setDirection,
  getContext: () => getAppContext<RegisteredContext>(),
  setContext: (next) => setAppContext<RegisteredContext>(next),
  getState: getNavigationState,
  getPendingLink,
  openPendingLink,
  clearPendingLink: () => setPendingLink(null),
  on: onNavigationEvent,
};
