import * as engine from '../../engine/commands';
import { placeOf, type ScreenPlace } from '../../engine/places';
import { onNavigationEvent } from '../events/navigationEvents';
import { getNavigationState, onNavigationStateChange } from '../state/navigationState';
import { command } from './errors';

type MenuSide = 'left' | 'right';

// A menu that doesn't tell it moved (a menu screen that never appears) still settles the command after this.
const MOVE_TIMEOUT = 1000;

// The screen of the center the user saw last, by side menu: the commands of a menu screen act from it. And the side
// that is open, by side menu, as its screens appear and disappear (the state comes a moment after them).
const centers = new Map<string, string>();
const open = new Map<string, MenuSide>();
let watching = false;

/** Keeps the center the user saw last and the open menu of each side menu; the app starts it with its state. */
export function watchSideMenus(): void {
  if (watching) return;
  watching = true;
  onNavigationStateChange(({ screen }) => {
    const menu = screen && placeOf(screen.id)?.menu;
    if (screen && menu?.part === 'center') centers.set(menu.id, screen.id);
  });
  onNavigationEvent('screenDidAppear', ({ screen }) => {
    const menu = placeOf(screen.id)?.menu;
    if (menu && menu.part !== 'center') open.set(menu.id, menu.part);
  });
  onNavigationEvent('screenDidDisappear', ({ screen }) => {
    const menu = placeOf(screen.id)?.menu;
    if (menu && menu.part !== 'center' && open.get(menu.id) === menu.part) open.delete(menu.id);
  });
}

/** The screen of the center a menu screen acts from, when the menu has one. */
export function centerOf(menuId: string): string | undefined {
  return centers.get(menuId);
}

function menuOf(name: string, screenId: string): NonNullable<ScreenPlace['menu']> {
  const place = placeOf(screenId);
  if (!place?.menu) {
    throw new Error(`[navigation] ${name}: ${place?.name ?? screenId} is not in a side menu`);
  }
  return place.menu;
}

/** The side of the side menu that is open. */
function openSide(menuId: string): MenuSide | undefined {
  return open.get(menuId);
}

/** The part of the side menu the state shows; none while its screen is elsewhere, such as in a modal over it. */
function partInState(menuId: string): NonNullable<ScreenPlace['menu']>['part'] | undefined {
  const screen = getNavigationState().screen;
  const menu = screen && placeOf(screen.id)?.menu;
  return menu?.id === menuId ? menu.part : undefined;
}

/**
 * Resolves once a screen of the side of the menu appears or disappears and the state shows it, at the latest after a
 * while. The native side sends the state a moment after the screen events, and a command right after the move acts
 * from the screen the state shows: the center after `closeMenu`, not the menu that has closed.
 */
function whenMenuMoved(
  event: 'screenDidAppear' | 'screenDidDisappear',
  menuId: string,
  side: MenuSide
): Promise<void> {
  // A state that shows a screen outside the side menu won't show the move: the command settles with the event.
  const shows =
    event === 'screenDidAppear'
      ? (part: string | undefined) => part === undefined || part === side
      : (part: string | undefined) => part !== side;
  return new Promise((resolve) => {
    let moved = false;
    const done = () => {
      clearTimeout(timer);
      stopEvent();
      stopState();
      resolve();
    };
    const settle = () => {
      if (moved && shows(partInState(menuId))) done();
    };
    const timer = setTimeout(done, MOVE_TIMEOUT);
    const stopEvent = onNavigationEvent(event, ({ screen }) => {
      const menu = placeOf(screen.id)?.menu;
      if (menu?.id !== menuId || menu.part !== side) return;
      moved = true;
      settle();
    });
    const stopState = onNavigationStateChange(settle);
  });
}

/** Opens a menu of the side menu the screen is in; resolves once it is open and the state shows its screen. */
export function openMenu(screenId: string, side: MenuSide): Promise<void> {
  return command('openMenu', () => {
    const menu = menuOf('openMenu', screenId);
    if (openSide(menu.id) === side) return Promise.resolve();
    const moved = whenMenuMoved('screenDidAppear', menu.id, side);
    engine.mergeOptions(screenId, { sideMenu: { [side]: { visible: true } } });
    return moved;
  });
}

/**
 * Closes the menu that is open in the side menu the screen is in; resolves once it is closed and the state shows the
 * screen under it.
 */
export function closeMenu(screenId: string): Promise<void> {
  return command('closeMenu', () => {
    const menu = menuOf('closeMenu', screenId);
    const side = openSide(menu.id);
    if (!side) return Promise.resolve();
    const moved = whenMenuMoved('screenDidDisappear', menu.id, side);
    engine.mergeOptions(screenId, { sideMenu: { [side]: { visible: false } } });
    return moved;
  });
}
