import type { Layout } from '../../interfaces/Layout';
import type { Options, OptionsSideMenu, SideMenuSide } from '../../interfaces/Options';
import { mergeOptions } from '../../engine/mergeOptions';
import {
  BUILD,
  buildContent,
  type LayoutContent,
  type LayoutElement,
  onPlatform,
  type ScreenUse,
  type ShowsScreens,
  type UsesOf,
} from './element';

/** The parts of a side menu: a screen named alone is a screen in a menu, and gets a stack in the center. */
export interface SideMenuParts {
  left?: LayoutContent;
  center: LayoutContent;
  right?: LayoutContent;
}

/** Side menus around a center. */
export class SideMenuElement<Uses extends ScreenUse = ScreenUse> implements LayoutElement<Uses> {
  // No command takes the id of a side menu: its screens open and close it (`openMenu`, `closeMenu`).
  constructor(
    private readonly parts: SideMenuParts,
    private readonly built: Options = {}
  ) {}

  private withMenu(sideMenu: OptionsSideMenu): SideMenuElement<Uses> {
    return new SideMenuElement(this.parts, mergeOptions(this.built, { sideMenu }));
  }

  left(left: SideMenuSide): SideMenuElement<Uses> {
    return this.withMenu({ left });
  }

  right(right: SideMenuSide): SideMenuElement<Uses> {
    return this.withMenu({ right });
  }

  /** #### (iOS specific) Where a swipe opens a menu. */
  gesture(openGestureMode: NonNullable<OptionsSideMenu['openGestureMode']>): SideMenuElement<Uses> {
    return this.withMenu({ openGestureMode });
  }

  /** #### (iOS specific) */
  animation(animationType: NonNullable<OptionsSideMenu['animationType']>): SideMenuElement<Uses> {
    return this.withMenu({ animationType });
  }

  /** Modifiers for iOS only. */
  ios(build: (menu: SideMenuElement<Uses>) => SideMenuElement<Uses>): SideMenuElement<Uses> {
    return onPlatform<SideMenuElement<Uses>>('ios', this, build);
  }

  /** Modifiers for Android only. */
  android(build: (menu: SideMenuElement<Uses>) => SideMenuElement<Uses>): SideMenuElement<Uses> {
    return onPlatform<SideMenuElement<Uses>>('android', this, build);
  }

  /** Options of the side menu no modifier covers. The options of screens go on the screens. */
  options(options: OptionsSideMenu): SideMenuElement<Uses> {
    return this.withMenu(options);
  }

  [BUILD](): Layout {
    const { left, center, right } = this.parts;
    return {
      sideMenu: {
        ...(left && { left: buildContent(left, 'screen') }),
        center: buildContent(center, 'stack'),
        ...(right && { right: buildContent(right, 'screen') }),
        options: this.built,
      },
    };
  }
}

// The type of an element carries the screens it shows.
export interface SideMenuElement<Uses extends ScreenUse = ScreenUse> extends ShowsScreens<Uses> {}

export function SideMenu<const Parts extends SideMenuParts>(
  parts: Parts
): SideMenuElement<UsesOf<Parts[keyof Parts]>> {
  return new SideMenuElement(parts);
}
